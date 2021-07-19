package io.rgb.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewTreeObserver
import android.webkit.MimeTypeMap
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.doOnPreDraw
import io.pixel.android.R
import io.pixel.config.ImageRequest
import io.rgb.image.ImageSize
import io.rgb.loader.load.ViewTargetRequestManager
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import kotlin.coroutines.resume

/**
 * @author conghai on 14/07/2021.
 */
internal inline val View.isAttachedToWindowCompat: Boolean
    get() = ViewCompat.isAttachedToWindow(this)

internal val ImageView.requestManager: ViewTargetRequestManager
    get() {
        var manager = getTag(R.id.request_manager) as? ViewTargetRequestManager
        if (manager == null) {
            manager = synchronized(this) {
                // Check again in case coil_request_manager was just set.
                (getTag(R.id.request_manager) as? ViewTargetRequestManager)
                    ?.let { return@synchronized it }

                ViewTargetRequestManager(this).apply {
                    addOnAttachStateChangeListener(this)
                    setTag(R.id.request_manager, this)
                }
            }
        }
        return manager
    }

internal val View.actualSize
    get() = ImageSize(width = width, height = height)

internal suspend fun View.awaitSize(): ImageSize {
    // Fast path: the view is already measured.
    actualSize.takeIf { it.isUndefined.not() }?.let { return it }

    // Slow path: wait for the view to be measured.
    return suspendCancellableCoroutine { continuation ->
        val viewTreeObserver = viewTreeObserver

        val preDrawListener = object : ViewTreeObserver.OnPreDrawListener {
            private var isResumed = false

            override fun onPreDraw(): Boolean {
                actualSize.takeIf { it.isUndefined.not() }?.let {
                    removePreDrawListenerSafe(viewTreeObserver, this)

                    if (!isResumed) {
                        isResumed = true
                        continuation.resume(it)
                    }
                }
                return true
            }
        }

        viewTreeObserver.addOnPreDrawListener(preDrawListener)

        continuation.invokeOnCancellation {
            removePreDrawListenerSafe(viewTreeObserver, preDrawListener)
        }
    }
}

private fun View.removePreDrawListenerSafe(
    previous: ViewTreeObserver,
    victim: ViewTreeObserver.OnPreDrawListener
) {
    if (previous.isAlive) {
        previous.removeOnPreDrawListener(victim)
    } else {
        viewTreeObserver.removeOnPreDrawListener(victim)
    }
}

internal fun lazyCallFactory(initializer: () -> Call.Factory): Call.Factory {
    val lazy: Lazy<Call.Factory> = lazy(initializer)
    return Call.Factory { lazy.value.newCall(it) } // Intentionally not a method reference.
}

internal fun Bitmap.toDrawable(context: Context): BitmapDrawable = toDrawable(context.resources)

internal fun MimeTypeMap.getMimeTypeFromUrl(url: String?): String? {
    if (url.isNullOrBlank()) {
        return null
    }

    val extension = url
        .substringBeforeLast('#') // Strip the fragment.
        .substringBeforeLast('?') // Strip the query.
        .substringAfterLast('/') // Get the last path segment.
        .substringAfterLast('.', missingDelimiterValue = "") // Get the file extension.

    return getMimeTypeFromExtension(extension)
}

internal val Configuration.nightMode: Int
    get() = uiMode and Configuration.UI_MODE_NIGHT_MASK