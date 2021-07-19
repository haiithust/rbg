package io.rgb.loader.load

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import io.rgb.image.ImageRequest
import io.rgb.image.ImageSize
import io.rgb.image.size
import io.rgb.loader.cache.CacheManager
import io.rgb.loader.fetcher.Fetcher
import io.rgb.utils.actualSize
import io.rgb.utils.awaitSize
import io.rgb.utils.toDrawable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class ImageLoadExecution(
    private val target: ImageView,
    private val request: ImageRequest,
    private val fetcher: Fetcher<Uri>,
    private val uri: Uri,
    private val cacheManager: CacheManager,
    private val dispatcher: CoroutineDispatcher
) {
    private val id = request.url

    private fun setPlaceholder(drawable: Drawable?) = target.setImageDrawable(drawable)

    private suspend fun setImageSize(): ImageSize {
        request.size.takeIf { it.isUndefined.not() }?.let { return it }
        target.actualSize.takeIf { it.isUndefined.not() }?.let { return it }
        return target.awaitSize()
    }

    suspend operator fun invoke() {
        Timber.d("Start load image view ${target.hashCode()} - $id")
        try {
            setPlaceholder(request.placeHolder)
            val size = setImageSize()
            val drawable = withContext(dispatcher) {
                val key = fetcher.key(uri).orEmpty()
                cacheManager[key]?.let {
                    return@withContext it.toDrawable(target.context) //TODO(Check bitmap valid)
                }
                val result = fetcher.fetch(uri, size)
                if (result is BitmapDrawable && key.isNotEmpty()) {
                    cacheManager[key] = result.bitmap
                }
                return@withContext result
            }
            target.setImageDrawable(drawable)
        } catch (e: Exception) {
            Timber.d("${request.url} - ${e.message}")
        } finally {

        }
    }
}