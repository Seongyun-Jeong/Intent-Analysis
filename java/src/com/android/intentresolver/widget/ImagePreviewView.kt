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

package com.android.intentresolver.widget

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import com.android.intentresolver.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.function.Consumer
import com.android.internal.R as IntR

typealias ImageLoader = suspend (Uri) -> Bitmap?

private const val IMAGE_FADE_IN_MILLIS = 150L

class ImagePreviewView : RelativeLayout {

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(
        context: Context, attrs: AttributeSet?, defStyleAttr: Int
    ) : this(context, attrs, defStyleAttr, 0)

    constructor(
        context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    private val coroutineScope = MainScope()
    private lateinit var mainImage: RoundedRectImageView
    private lateinit var secondLargeImage: RoundedRectImageView
    private lateinit var secondSmallImage: RoundedRectImageView
    private lateinit var thirdImage: RoundedRectImageView

    private var loadImageJob: Job? = null
    private var onTransitionViewReadyCallback: Consumer<Boolean>? = null

    override fun onFinishInflate() {
        LayoutInflater.from(context).inflate(R.layout.image_preview_view, this, true)
        mainImage = requireViewById(IntR.id.content_preview_image_1_large)
        secondLargeImage = requireViewById(IntR.id.content_preview_image_2_large)
        secondSmallImage = requireViewById(IntR.id.content_preview_image_2_small)
        thirdImage = requireViewById(IntR.id.content_preview_image_3_small)
    }

    /**
     * Specifies a transition animation target name and a readiness callback. The callback will be
     * invoked once when the view preparation is done i.e. either when an image is loaded into it
     * and it is laid out (and it is ready to be draw) or image loading has failed.
     * Should be called before [setImages].
     * @param name, transition name
     * @param onViewReady, a callback that will be invoked with `true` if the view is ready to
     * receive transition animation (the image was loaded successfully) and with `false` otherwise.
     */
    fun setSharedElementTransitionTarget(name: String, onViewReady: Consumer<Boolean>) {
        mainImage.transitionName = name
        onTransitionViewReadyCallback = onViewReady
    }

    fun setImages(uris: List<Uri>, imageLoader: ImageLoader) {
        loadImageJob?.cancel()
        loadImageJob = coroutineScope.launch {
            when (uris.size) {
                0 -> hideAllViews()
                1 -> showOneImage(uris, imageLoader)
                2 -> showTwoImages(uris, imageLoader)
                else -> showThreeImages(uris, imageLoader)
            }
        }
    }

    private fun hideAllViews() {
        mainImage.isVisible = false
        secondLargeImage.isVisible = false
        secondSmallImage.isVisible = false
        thirdImage.isVisible = false
        invokeTransitionViewReadyCallback(runTransitionAnimation = false)
    }

    private suspend fun showOneImage(uris: List<Uri>, imageLoader: ImageLoader) {
        secondLargeImage.isVisible = false
        secondSmallImage.isVisible = false
        thirdImage.isVisible = false
        showImages(uris, imageLoader, mainImage)
    }

    private suspend fun showTwoImages(uris: List<Uri>, imageLoader: ImageLoader) {
        secondSmallImage.isVisible = false
        thirdImage.isVisible = false
        showImages(uris, imageLoader, mainImage, secondLargeImage)
    }

    private suspend fun showThreeImages(uris: List<Uri>, imageLoader: ImageLoader) {
        secondLargeImage.isVisible = false
        showImages(uris, imageLoader, mainImage, secondSmallImage, thirdImage)
        thirdImage.setExtraImageCount(uris.size - 3)
    }

    private suspend fun showImages(
        uris: List<Uri>, imageLoader: ImageLoader, vararg views: RoundedRectImageView
    ) = coroutineScope {
        for (i in views.indices) {
            launch {
                loadImageIntoView(views[i], uris[i], imageLoader)
            }
        }
    }

    private suspend fun loadImageIntoView(
        view: RoundedRectImageView, uri: Uri, imageLoader: ImageLoader
    ) {
        val bitmap = runCatching {
            imageLoader(uri)
        }.getOrDefault(null)
        if (bitmap == null) {
            view.isVisible = false
            if (view === mainImage) {
                invokeTransitionViewReadyCallback(runTransitionAnimation = false)
            }
        } else {
            view.isVisible = true
            view.setImageBitmap(bitmap)

            view.alpha = 0f
            ObjectAnimator.ofFloat(view, "alpha", 0.0f, 1.0f).apply {
                interpolator = DecelerateInterpolator(1.0f)
                duration = IMAGE_FADE_IN_MILLIS
                start()
            }
            if (view === mainImage && onTransitionViewReadyCallback != null) {
                setupPreDrawListener(mainImage)
            }
        }
    }

    private fun setupPreDrawListener(view: View) {
        view.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    view.viewTreeObserver.removeOnPreDrawListener(this)
                    invokeTransitionViewReadyCallback(runTransitionAnimation = true)
                    return true
                }
            }
        )
    }

    private fun invokeTransitionViewReadyCallback(runTransitionAnimation: Boolean) {
        onTransitionViewReadyCallback?.accept(runTransitionAnimation)
        onTransitionViewReadyCallback = null
    }
}
