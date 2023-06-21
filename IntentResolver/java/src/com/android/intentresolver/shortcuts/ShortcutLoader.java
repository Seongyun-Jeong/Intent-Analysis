/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.intentresolver.shortcuts;

import android.app.ActivityManager;
import android.app.prediction.AppPredictor;
import android.app.prediction.AppTarget;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.ApplicationInfoFlags;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.os.AsyncTask;
import android.os.UserHandle;
import android.os.UserManager;
import android.service.chooser.ChooserTarget;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.android.intentresolver.chooser.DisplayResolveInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Encapsulates shortcuts loading logic from either AppPredictor or ShortcutManager.
 * <p>
 *     A ShortcutLoader instance can be viewed as a per-profile singleton hot stream of shortcut
 * updates. The shortcut loading is triggered by the {@link #queryShortcuts(DisplayResolveInfo[])},
 * the processing will happen on the {@link #mBackgroundExecutor} and the result is delivered
 * through the {@link #mCallback} on the {@link #mCallbackExecutor}, the main thread.
 * </p>
 * <p>
 *    The current version does not improve on the legacy in a way that it does not guarantee that
 * each invocation of the {@link #queryShortcuts(DisplayResolveInfo[])} will be matched by an
 * invocation of the callback (there are early terminations of the flow). Also, the fetched
 * shortcuts would be matched against the last known input, i.e. two invocations of
 * {@link #queryShortcuts(DisplayResolveInfo[])} may result in two callbacks where shortcuts are
 * processed against the latest input.
 * </p>
 */
public class ShortcutLoader {
    private static final String TAG = "ChooserActivity";

    private static final Request NO_REQUEST = new Request(new DisplayResolveInfo[0]);

    private final Context mContext;
    @Nullable
    private final AppPredictorProxy mAppPredictor;
    private final UserHandle mUserHandle;
    @Nullable
    private final IntentFilter mTargetIntentFilter;
    private final Executor mBackgroundExecutor;
    private final Executor mCallbackExecutor;
    private final boolean mIsPersonalProfile;
    private final ShortcutToChooserTargetConverter mShortcutToChooserTargetConverter =
            new ShortcutToChooserTargetConverter();
    private final UserManager mUserManager;
    private final AtomicReference<Consumer<Result>> mCallback = new AtomicReference<>();
    private final AtomicReference<Request> mActiveRequest = new AtomicReference<>(NO_REQUEST);

    @Nullable
    private final AppPredictor.Callback mAppPredictorCallback;

    @MainThread
    public ShortcutLoader(
            Context context,
            @Nullable AppPredictor appPredictor,
            UserHandle userHandle,
            @Nullable IntentFilter targetIntentFilter,
            Consumer<Result> callback) {
        this(
                context,
                appPredictor == null ? null : new AppPredictorProxy(appPredictor),
                userHandle,
                userHandle.equals(UserHandle.of(ActivityManager.getCurrentUser())),
                targetIntentFilter,
                AsyncTask.SERIAL_EXECUTOR,
                context.getMainExecutor(),
                callback);
    }

    @VisibleForTesting
    ShortcutLoader(
            Context context,
            @Nullable AppPredictorProxy appPredictor,
            UserHandle userHandle,
            boolean isPersonalProfile,
            @Nullable IntentFilter targetIntentFilter,
            Executor backgroundExecutor,
            Executor callbackExecutor,
            Consumer<Result> callback) {
        mContext = context;
        mAppPredictor = appPredictor;
        mUserHandle = userHandle;
        mTargetIntentFilter = targetIntentFilter;
        mBackgroundExecutor = backgroundExecutor;
        mCallbackExecutor = callbackExecutor;
        mCallback.set(callback);
        mIsPersonalProfile = isPersonalProfile;
        mUserManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);

        if (mAppPredictor != null) {
            mAppPredictorCallback = createAppPredictorCallback();
            mAppPredictor.registerPredictionUpdates(mCallbackExecutor, mAppPredictorCallback);
        } else {
            mAppPredictorCallback = null;
        }
    }

    /**
     * Unsubscribe from app predictor if one was provided.
     */
    @MainThread
    public void destroy() {
        if (mCallback.getAndSet(null) != null) {
            if (mAppPredictor != null) {
                mAppPredictor.unregisterPredictionUpdates(mAppPredictorCallback);
            }
        }
    }

    private boolean isDestroyed() {
        return mCallback.get() == null;
    }

    /**
     * Set new resolved targets. This will trigger shortcut loading.
     * @param appTargets a collection of application targets a loaded set of shortcuts will be
     *                   grouped against
     */
    @MainThread
    public void queryShortcuts(DisplayResolveInfo[] appTargets) {
        if (isDestroyed()) {
            return;
        }
        mActiveRequest.set(new Request(appTargets));
        mBackgroundExecutor.execute(this::loadShortcuts);
    }

    @WorkerThread
    private void loadShortcuts() {
        // no need to query direct share for work profile when its locked or disabled
        if (!shouldQueryDirectShareTargets()) {
            return;
        }
        Log.d(TAG, "querying direct share targets");
        queryDirectShareTargets(false);
    }

    @WorkerThread
    private void queryDirectShareTargets(boolean skipAppPredictionService) {
        if (isDestroyed()) {
            return;
        }
        if (!skipAppPredictionService && mAppPredictor != null) {
            mAppPredictor.requestPredictionUpdate();
            return;
        }
        // Default to just querying ShortcutManager if AppPredictor not present.
        if (mTargetIntentFilter == null) {
            return;
        }

        Context selectedProfileContext = mContext.createContextAsUser(mUserHandle, 0 /* flags */);
        ShortcutManager sm = (ShortcutManager) selectedProfileContext
                .getSystemService(Context.SHORTCUT_SERVICE);
        List<ShortcutManager.ShareShortcutInfo> shortcuts =
                sm.getShareTargets(mTargetIntentFilter);
        sendShareShortcutInfoList(shortcuts, false, null);
    }

    private AppPredictor.Callback createAppPredictorCallback() {
        return appPredictorTargets -> {
            if (appPredictorTargets.isEmpty() && shouldQueryDirectShareTargets()) {
                // APS may be disabled, so try querying targets ourselves.
                queryDirectShareTargets(true);
                return;
            }

            final List<ShortcutManager.ShareShortcutInfo> shortcuts = new ArrayList<>();
            List<AppTarget> shortcutResults = new ArrayList<>();
            for (AppTarget appTarget : appPredictorTargets) {
                if (appTarget.getShortcutInfo() == null) {
                    continue;
                }
                shortcutResults.add(appTarget);
            }
            appPredictorTargets = shortcutResults;
            for (AppTarget appTarget : appPredictorTargets) {
                shortcuts.add(new ShortcutManager.ShareShortcutInfo(
                        appTarget.getShortcutInfo(),
                        new ComponentName(appTarget.getPackageName(), appTarget.getClassName())));
            }
            sendShareShortcutInfoList(shortcuts, true, appPredictorTargets);
        };
    }

    @WorkerThread
    private void sendShareShortcutInfoList(
            List<ShortcutManager.ShareShortcutInfo> shortcuts,
            boolean isFromAppPredictor,
            @Nullable List<AppTarget> appPredictorTargets) {
        if (appPredictorTargets != null && appPredictorTargets.size() != shortcuts.size()) {
            throw new RuntimeException("resultList and appTargets must have the same size."
                    + " resultList.size()=" + shortcuts.size()
                    + " appTargets.size()=" + appPredictorTargets.size());
        }
        Context selectedProfileContext = mContext.createContextAsUser(mUserHandle, 0 /* flags */);
        for (int i = shortcuts.size() - 1; i >= 0; i--) {
            final String packageName = shortcuts.get(i).getTargetComponent().getPackageName();
            if (!isPackageEnabled(selectedProfileContext, packageName)) {
                shortcuts.remove(i);
                if (appPredictorTargets != null) {
                    appPredictorTargets.remove(i);
                }
            }
        }

        HashMap<ChooserTarget, AppTarget> directShareAppTargetCache = new HashMap<>();
        HashMap<ChooserTarget, ShortcutInfo> directShareShortcutInfoCache = new HashMap<>();
        // Match ShareShortcutInfos with DisplayResolveInfos to be able to use the old code path
        // for direct share targets. After ShareSheet is refactored we should use the
        // ShareShortcutInfos directly.
        final DisplayResolveInfo[] appTargets = mActiveRequest.get().appTargets;
        List<ShortcutResultInfo> resultRecords = new ArrayList<>();
        for (DisplayResolveInfo displayResolveInfo : appTargets) {
            List<ShortcutManager.ShareShortcutInfo> matchingShortcuts =
                    filterShortcutsByTargetComponentName(
                            shortcuts, displayResolveInfo.getResolvedComponentName());
            if (matchingShortcuts.isEmpty()) {
                continue;
            }

            List<ChooserTarget> chooserTargets = mShortcutToChooserTargetConverter
                    .convertToChooserTarget(
                            matchingShortcuts,
                            shortcuts,
                            appPredictorTargets,
                            directShareAppTargetCache,
                            directShareShortcutInfoCache);

            ShortcutResultInfo resultRecord =
                    new ShortcutResultInfo(displayResolveInfo, chooserTargets);
            resultRecords.add(resultRecord);
        }

        postReport(
                new Result(
                        isFromAppPredictor,
                        appTargets,
                        resultRecords.toArray(new ShortcutResultInfo[0]),
                        directShareAppTargetCache,
                        directShareShortcutInfoCache));
    }

    private void postReport(Result result) {
        mCallbackExecutor.execute(() -> report(result));
    }

    @MainThread
    private void report(Result result) {
        Consumer<Result> callback = mCallback.get();
        if (callback != null) {
            callback.accept(result);
        }
    }

    /**
     * Returns {@code false} if {@code userHandle} is the work profile and it's either
     * in quiet mode or not running.
     */
    private boolean shouldQueryDirectShareTargets() {
        return mIsPersonalProfile || isProfileActive();
    }

    @VisibleForTesting
    protected boolean isProfileActive() {
        return mUserManager.isUserRunning(mUserHandle)
                && mUserManager.isUserUnlocked(mUserHandle)
                && !mUserManager.isQuietModeEnabled(mUserHandle);
    }

    private static boolean isPackageEnabled(Context context, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }
        ApplicationInfo appInfo;
        try {
            appInfo = context.getPackageManager().getApplicationInfo(
                    packageName,
                    ApplicationInfoFlags.of(PackageManager.GET_META_DATA));
        } catch (NameNotFoundException e) {
            return false;
        }

        return appInfo != null && appInfo.enabled
                && (appInfo.flags & ApplicationInfo.FLAG_SUSPENDED) == 0;
    }

    private static List<ShortcutManager.ShareShortcutInfo> filterShortcutsByTargetComponentName(
            List<ShortcutManager.ShareShortcutInfo> allShortcuts, ComponentName requiredTarget) {
        List<ShortcutManager.ShareShortcutInfo> matchingShortcuts = new ArrayList<>();
        for (ShortcutManager.ShareShortcutInfo shortcut : allShortcuts) {
            if (requiredTarget.equals(shortcut.getTargetComponent())) {
                matchingShortcuts.add(shortcut);
            }
        }
        return matchingShortcuts;
    }

    private static class Request {
        public final DisplayResolveInfo[] appTargets;

        Request(DisplayResolveInfo[] targets) {
            appTargets = targets;
        }
    }

    /**
     * Resolved shortcuts with corresponding app targets.
     */
    public static class Result {
        public final boolean isFromAppPredictor;
        /**
         * Input app targets (see {@link ShortcutLoader#queryShortcuts(DisplayResolveInfo[])} the
         * shortcuts were process against.
         */
        public final DisplayResolveInfo[] appTargets;
        /**
         * Shortcuts grouped by app target.
         */
        public final ShortcutResultInfo[] shortcutsByApp;
        public final Map<ChooserTarget, AppTarget> directShareAppTargetCache;
        public final Map<ChooserTarget, ShortcutInfo> directShareShortcutInfoCache;

        @VisibleForTesting
        public Result(
                boolean isFromAppPredictor,
                DisplayResolveInfo[] appTargets,
                ShortcutResultInfo[] shortcutsByApp,
                Map<ChooserTarget, AppTarget> directShareAppTargetCache,
                Map<ChooserTarget, ShortcutInfo> directShareShortcutInfoCache) {
            this.isFromAppPredictor = isFromAppPredictor;
            this.appTargets = appTargets;
            this.shortcutsByApp = shortcutsByApp;
            this.directShareAppTargetCache = directShareAppTargetCache;
            this.directShareShortcutInfoCache = directShareShortcutInfoCache;
        }
    }

    /**
     * Shortcuts grouped by app.
     */
    public static class ShortcutResultInfo {
        public final DisplayResolveInfo appTarget;
        public final List<ChooserTarget> shortcuts;

        public ShortcutResultInfo(DisplayResolveInfo appTarget, List<ChooserTarget> shortcuts) {
            this.appTarget = appTarget;
            this.shortcuts = shortcuts;
        }
    }

    /**
     * A wrapper around AppPredictor to facilitate unit-testing.
     */
    @VisibleForTesting
    public static class AppPredictorProxy {
        private final AppPredictor mAppPredictor;

        AppPredictorProxy(AppPredictor appPredictor) {
            mAppPredictor = appPredictor;
        }

        /**
         * {@link AppPredictor#registerPredictionUpdates}
         */
        public void registerPredictionUpdates(
                Executor callbackExecutor, AppPredictor.Callback callback) {
            mAppPredictor.registerPredictionUpdates(callbackExecutor, callback);
        }

        /**
         * {@link AppPredictor#unregisterPredictionUpdates}
         */
        public void unregisterPredictionUpdates(AppPredictor.Callback callback) {
            mAppPredictor.unregisterPredictionUpdates(callback);
        }

        /**
         * {@link AppPredictor#requestPredictionUpdate}
         */
        public void requestPredictionUpdate() {
            mAppPredictor.requestPredictionUpdate();
        }
    }
}
