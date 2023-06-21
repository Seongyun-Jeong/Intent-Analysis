/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.intentresolver;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.util.Size;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Delegate to manage deferred resource loads for content preview assets, while
 * implementing Chooser's application logic for determining timeout/success/failure conditions.
 */
public class ChooserContentPreviewCoordinator implements
        ChooserContentPreviewUi.ContentPreviewCoordinator {
    public ChooserContentPreviewCoordinator(
            ExecutorService backgroundExecutor,
            ChooserActivity chooserActivity,
            Runnable onFailCallback) {
        this.mBackgroundExecutor = MoreExecutors.listeningDecorator(backgroundExecutor);
        this.mChooserActivity = chooserActivity;
        this.mOnFailCallback = onFailCallback;

        this.mImageLoadTimeoutMillis =
                chooserActivity.getResources().getInteger(R.integer.config_shortAnimTime);
    }

    @Override
    public void loadImage(final Uri imageUri, final Consumer<Bitmap> callback) {
        final int size = mChooserActivity.getResources().getDimensionPixelSize(
                R.dimen.chooser_preview_image_max_dimen);

        // TODO: apparently this timeout is only used for not holding shared element transition
        //  animation for too long. If so, we already have a better place for it
        //  EnterTransitionAnimationDelegate.
        mHandler.postDelayed(this::onWatchdogTimeout, mImageLoadTimeoutMillis);

        ListenableFuture<Bitmap> bitmapFuture = mBackgroundExecutor.submit(
                () -> mChooserActivity.loadThumbnail(imageUri, new Size(size, size)));

        Futures.addCallback(
                bitmapFuture,
                new FutureCallback<Bitmap>() {
                    @Override
                    public void onSuccess(Bitmap loadedBitmap) {
                        try {
                            callback.accept(loadedBitmap);
                            onLoadCompleted(loadedBitmap);
                        } catch (Exception e) { /* unimportant */ }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        callback.accept(null);
                    }
                },
                mHandler::post);
    }

    private final ChooserActivity mChooserActivity;
    private final ListeningExecutorService mBackgroundExecutor;
    private final Runnable mOnFailCallback;
    private final int mImageLoadTimeoutMillis;

    // TODO: this uses a `Handler` because there doesn't seem to be a straightforward way to get a
    // `ScheduledExecutorService` that posts to the UI thread unless we use Dagger. Eventually we'll
    // use Dagger and can inject this as a `@UiThread ScheduledExecutorService`.
    private final Handler mHandler = new Handler();

    private boolean mAtLeastOneLoaded = false;

    @MainThread
    private void onWatchdogTimeout() {
        if (mChooserActivity.isFinishing()) {
            return;
        }

        // If at least one image loads within the timeout period, allow other loads to continue.
        if (!mAtLeastOneLoaded) {
            mOnFailCallback.run();
        }
    }

    @MainThread
    private void onLoadCompleted(@Nullable Bitmap loadedBitmap) {
        if (mChooserActivity.isFinishing()) {
            return;
        }

        // TODO: the following logic can be described as "invoke the fail callback when the first
        //  image loading has failed". Historically, before we had switched from a single-threaded
        //  pool to a multi-threaded pool, we first loaded the transition element's image (the image
        //  preview is the only case when those callbacks matter) and aborting the animation on it's
        //  failure was reasonable. With the multi-thread pool, the first result may belong to any
        //  image and thus we can falsely abort the animation.
        //  Now, when we track the transition view state directly and after the timeout logic will
        //  be moved into ChooserActivity$EnterTransitionAnimationDelegate, we can just get rid of
        //  the fail callback and the following logic altogether.
        mAtLeastOneLoaded |= loadedBitmap != null;
        boolean wholeBatchFailed = !mAtLeastOneLoaded;

        if (wholeBatchFailed) {
            mOnFailCallback.run();
        }
    }
}
