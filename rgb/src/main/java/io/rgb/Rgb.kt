package io.rgb

import android.content.Context
import android.widget.ImageView
import io.rgb.android.BuildConfig
import io.rgb.image.ImageRequest
import io.rgb.loader.ImageLoaderManager
import kotlinx.coroutines.*
import timber.log.Timber

object Rgb {
    init {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    @JvmStatic
    fun load(
        url: String? = null,
        target: ImageView,
        builder: ImageRequest.Builder.() -> Unit = {},
    ): Job {
        assert(target.context != null) { "Context of target view invalid" }
        val request = ImageRequest.Builder(url.orEmpty())
            .apply(builder)
            .build()
        return ImageLoaderManager.getInstance(target.context).enqueue(target, request)
    }

    fun load(target: ImageView, request: ImageRequest): Job = ImageLoaderManager.getInstance(target.context).enqueue(target, request)

    fun release(context: Context) {
        ImageLoaderManager.getInstance(context).release()
    }
}

fun ImageView.load(url: String, builder: ImageRequest.Builder.() -> Unit = {}) {
    Rgb.load(url, this, builder)
}

