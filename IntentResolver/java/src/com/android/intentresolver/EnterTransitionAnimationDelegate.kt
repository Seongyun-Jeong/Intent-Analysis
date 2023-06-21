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
package com.android.intentresolver

import android.app.Activity
import android.app.SharedElementCallback
import android.view.View
import com.android.intentresolver.widget.ResolverDrawerLayout
import java.util.function.Supplier

/**
 * A helper class to track app's readiness for the scene transition animation.
 * The app is ready when both the image is laid out and the drawer offset is calculated.
 */
internal class EnterTransitionAnimationDelegate(
    private val activity: Activity,
    private val resolverDrawerLayoutSupplier: Supplier<ResolverDrawerLayout?>
) : View.OnLayoutChangeListener {
    private var removeSharedElements = false
    private var previewReady = false
    private var offsetCalculated = false

    init {
        activity.setEnterSharedElementCallback(
            object : SharedElementCallback() {
                override fun onMapSharedElements(
                    names: MutableList<String>, sharedElements: MutableMap<String, View>
                ) {
                    this@EnterTransitionAnimationDelegate.onMapSharedElements(
                        names, sharedElements
                    )
                }
            })
    }

    fun postponeTransition() = activity.postponeEnterTransition()

    fun markImagePreviewReady(runTransitionAnimation: Boolean) {
        if (!runTransitionAnimation) {
            removeSharedElements = true
        }
        if (!previewReady) {
            previewReady = true
            maybeStartListenForLayout()
        }
    }

    fun markOffsetCalculated() {
        if (!offsetCalculated) {
            offsetCalculated = true
            maybeStartListenForLayout()
        }
    }

    private fun onMapSharedElements(
        names: MutableList<String>,
        sharedElements: MutableMap<String, View>
    ) {
        if (removeSharedElements) {
            names.remove(ChooserActivity.FIRST_IMAGE_PREVIEW_TRANSITION_NAME)
            sharedElements.remove(ChooserActivity.FIRST_IMAGE_PREVIEW_TRANSITION_NAME)
        }
        removeSharedElements = false
    }

    private fun maybeStartListenForLayout() {
        val drawer = resolverDrawerLayoutSupplier.get()
        if (previewReady && offsetCalculated && drawer != null) {
            if (drawer.isInLayout) {
                startPostponedEnterTransition()
            } else {
                drawer.addOnLayoutChangeListener(this)
                drawer.requestLayout()
            }
        }
    }

    override fun onLayoutChange(
        v: View,
        left: Int, top: Int, right: Int, bottom: Int,
        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
    ) {
        v.removeOnLayoutChangeListener(this)
        startPostponedEnterTransition()
    }

    private fun startPostponedEnterTransition() {
        if (!removeSharedElements && activity.isActivityTransitionRunning) {
            // Disable the window animations as it interferes with the transition animation.
            activity.window.setWindowAnimations(0)
        }
        activity.startPostponedEnterTransition()
    }
}
