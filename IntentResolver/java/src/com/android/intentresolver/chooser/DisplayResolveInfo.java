/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.intentresolver.chooser;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.UserHandle;

import com.android.intentresolver.ResolverActivity;
import com.android.intentresolver.TargetPresentationGetter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A TargetInfo plus additional information needed to render it (such as icon and label) and
 * resolve it to an activity.
 */
public class DisplayResolveInfo implements TargetInfo {
    private final ResolveInfo mResolveInfo;
    private CharSequence mDisplayLabel;
    private CharSequence mExtendedInfo;
    private final Intent mResolvedIntent;
    private final List<Intent> mSourceIntents = new ArrayList<>();
    private final boolean mIsSuspended;
    private TargetPresentationGetter mPresentationGetter;
    private boolean mPinned = false;
    private final IconHolder mDisplayIconHolder = new SettableIconHolder();

    /** Create a new {@code DisplayResolveInfo} instance. */
    public static DisplayResolveInfo newDisplayResolveInfo(
            Intent originalIntent,
            ResolveInfo resolveInfo,
            @NonNull Intent resolvedIntent,
            @Nullable TargetPresentationGetter presentationGetter) {
        return newDisplayResolveInfo(
                originalIntent,
                resolveInfo,
                /* displayLabel=*/ null,
                /* extendedInfo=*/ null,
                resolvedIntent,
                presentationGetter);
    }

    /** Create a new {@code DisplayResolveInfo} instance. */
    public static DisplayResolveInfo newDisplayResolveInfo(
            Intent originalIntent,
            ResolveInfo resolveInfo,
            CharSequence displayLabel,
            CharSequence extendedInfo,
            @NonNull Intent resolvedIntent,
            @Nullable TargetPresentationGetter presentationGetter) {
        return new DisplayResolveInfo(
                originalIntent,
                resolveInfo,
                displayLabel,
                extendedInfo,
                resolvedIntent,
                presentationGetter);
    }

    private DisplayResolveInfo(
            Intent originalIntent,
            ResolveInfo resolveInfo,
            CharSequence displayLabel,
            CharSequence extendedInfo,
            @NonNull Intent resolvedIntent,
            @Nullable TargetPresentationGetter presentationGetter) {
        mSourceIntents.add(originalIntent);
        mResolveInfo = resolveInfo;
        mDisplayLabel = displayLabel;
        mExtendedInfo = extendedInfo;
        mPresentationGetter = presentationGetter;

        final ActivityInfo ai = mResolveInfo.activityInfo;
        mIsSuspended = (ai.applicationInfo.flags & ApplicationInfo.FLAG_SUSPENDED) != 0;

        final Intent intent = new Intent(resolvedIntent);
        intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT
                | Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
        intent.setComponent(new ComponentName(ai.applicationInfo.packageName, ai.name));
        mResolvedIntent = intent;
    }

    private DisplayResolveInfo(
            DisplayResolveInfo other,
            Intent fillInIntent,
            int flags,
            TargetPresentationGetter presentationGetter) {
        mSourceIntents.addAll(other.getAllSourceIntents());
        mResolveInfo = other.mResolveInfo;
        mIsSuspended = other.mIsSuspended;
        mDisplayLabel = other.mDisplayLabel;
        mExtendedInfo = other.mExtendedInfo;
        mResolvedIntent = new Intent(other.mResolvedIntent);
        mResolvedIntent.fillIn(fillInIntent, flags);
        mPresentationGetter = presentationGetter;

        mDisplayIconHolder.setDisplayIcon(other.mDisplayIconHolder.getDisplayIcon());
    }

    protected DisplayResolveInfo(DisplayResolveInfo other) {
        mSourceIntents.addAll(other.getAllSourceIntents());
        mResolveInfo = other.mResolveInfo;
        mIsSuspended = other.mIsSuspended;
        mDisplayLabel = other.mDisplayLabel;
        mExtendedInfo = other.mExtendedInfo;
        mResolvedIntent = other.mResolvedIntent;
        mPresentationGetter = other.mPresentationGetter;

        mDisplayIconHolder.setDisplayIcon(other.mDisplayIconHolder.getDisplayIcon());
    }

    @Override
    public final boolean isDisplayResolveInfo() {
        return true;
    }

    public ResolveInfo getResolveInfo() {
        return mResolveInfo;
    }

    public CharSequence getDisplayLabel() {
        if (mDisplayLabel == null && mPresentationGetter != null) {
            mDisplayLabel = mPresentationGetter.getLabel();
            mExtendedInfo = mPresentationGetter.getSubLabel();
        }
        return mDisplayLabel;
    }

    public boolean hasDisplayLabel() {
        return mDisplayLabel != null;
    }

    public void setDisplayLabel(CharSequence displayLabel) {
        mDisplayLabel = displayLabel;
    }

    public void setExtendedInfo(CharSequence extendedInfo) {
        mExtendedInfo = extendedInfo;
    }

    @Override
    public IconHolder getDisplayIconHolder() {
        return mDisplayIconHolder;
    }

    @Override
    public TargetInfo cloneFilledIn(Intent fillInIntent, int flags) {
        return new DisplayResolveInfo(this, fillInIntent, flags, mPresentationGetter);
    }

    @Override
    public List<Intent> getAllSourceIntents() {
        return mSourceIntents;
    }

    @Override
    public ArrayList<DisplayResolveInfo> getAllDisplayTargets() {
        return new ArrayList<>(Arrays.asList(this));
    }

    public void addAlternateSourceIntent(Intent alt) {
        mSourceIntents.add(alt);
    }

    public CharSequence getExtendedInfo() {
        return mExtendedInfo;
    }

    public Intent getResolvedIntent() {
        return mResolvedIntent;
    }

    @Override
    public ComponentName getResolvedComponentName() {
        return new ComponentName(mResolveInfo.activityInfo.packageName,
                mResolveInfo.activityInfo.name);
    }

    @Override
    public boolean start(Activity activity, Bundle options) {
        activity.startActivity(mResolvedIntent, options);
        return true;
    }

    @Override
    public boolean startAsCaller(ResolverActivity activity, Bundle options, int userId) {
        TargetInfo.prepareIntentForCrossProfileLaunch(mResolvedIntent, userId);
        activity.startActivityAsCaller(mResolvedIntent, options, false, userId);
        return true;
    }

    @Override
    public boolean startAsUser(Activity activity, Bundle options, UserHandle user) {
        TargetInfo.prepareIntentForCrossProfileLaunch(mResolvedIntent, user.getIdentifier());
        activity.startActivityAsUser(mResolvedIntent, options, user);
        return false;
    }

    public boolean isSuspended() {
        return mIsSuspended;
    }

    @Override
    public boolean isPinned() {
        return mPinned;
    }

    public void setPinned(boolean pinned) {
        mPinned = pinned;
    }
}
