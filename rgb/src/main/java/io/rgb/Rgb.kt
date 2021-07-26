package io.rgb

import android.content.Context
import android.net.Uri
import android.widget.ImageView
import androidx.annotation.DrawableRes
import io.rgb.android.BuildConfig
import io.rgb.image.ImageRequest
import io.rgb.loader.ImageLoaderManager
import kotlinx.coroutines.Job
import timber.log.Timber
import java.io.File

object Rgb {
    init {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    @JvmStatic
    fun load(
        data: Any,
        target: ImageView,
        builder: ImageRequest.Builder.() -> Unit = {},
    ): Job {
        assert(target.context != null) { "Context of target view invalid" }
        val request = ImageRequest.Builder(data)
            .apply(builder)
            .build()
        return ImageLoaderManager.getInstance(target.context).enqueue(target, request)
    }

    fun load(target: ImageView, request: ImageRequest): Job =
        ImageLoaderManager.getInstance(target.context).enqueue(target, request)

    fun release(context: Context) {
        ImageLoaderManager.getInstance(context).release()
    }
}

fun ImageView.load(uri: String, builder: ImageRequest.Builder.() -> Unit = {}) {
    Rgb.load(uri, this, builder)
}

fun ImageView.load(uri: Uri, builder: ImageRequest.Builder.() -> Unit = {}) {
    Rgb.load(uri.toString(), this, builder)
}

fun ImageView.load(file: File, builder: ImageRequest.Builder.() -> Unit = {}) {
    Rgb.load(file, this, builder)
}

fun ImageView.load(@DrawableRes id: Int, builder: ImageRequest.Builder.() -> Unit = {}) {
    Rgb.load(id, this, builder)
}

