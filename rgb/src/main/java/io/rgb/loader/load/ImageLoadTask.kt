package io.rgb.loader.load

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import io.rgb.image.ImageRequest
import io.rgb.image.ImageSize
import io.rgb.image.size
import io.rgb.loader.cache.BitmapCacheManager
import io.rgb.loader.decoder.DataSource
import io.rgb.loader.decoder.Decoder
import io.rgb.loader.fetcher.DrawableResult
import io.rgb.loader.fetcher.FetchResult
import io.rgb.loader.fetcher.Fetcher
import io.rgb.loader.fetcher.SourceResult
import io.rgb.utils.actualSize
import io.rgb.utils.awaitSize
import io.rgb.utils.toDrawable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.coroutineContext

internal class ImageLoadTask(
    private val context: Context,
    private val target: ImageView? = null,
    private val request: ImageRequest,
    private val fetcher: Fetcher<Uri>,
    private val uri: Uri,
    private val bitmapCacheManager: BitmapCacheManager,
    private val dispatcher: CoroutineDispatcher,
    private val decoder: Decoder
) {
    private fun setPlaceholder(drawable: Drawable?) = target?.setImageDrawable(drawable)

    private suspend fun setImageSize(): ImageSize {
        request.size.takeIf { it.isUndefined.not() }?.let { return it }
        if (target == null) return ImageSize.UNDEFINED
        target.actualSize.takeIf { it.isUndefined.not() }?.let { return it }
        return target.awaitSize()
    }

    suspend fun execute(): Drawable? {
        try {
            setPlaceholder(request.placeHolder)
            val size = setImageSize()
            Timber.d("Start load image view with size $size")
            val drawable = withContext(dispatcher) {
                val key = buildKey(fetcher.key(uri).orEmpty(), size)
                Timber.d("Load image $uri with $key")
                bitmapCacheManager.getMemoryCache(key)?.let {
                    Timber.d("Return Bitmap from Memory Cache")
                    return@withContext it.toDrawable(context)
                }
                if (request.options.readFromCacheEnable) {
                    bitmapCacheManager.getDiskCache(key)?.let {
                        Timber.d("Return Bitmap from Disk Cache")
                        return@withContext it.toDrawable(context)
                    }
                }
                val result = fetcher.fetch(uri, size)
                val drawable = decode(result, size)
                writeToCache(request.options, drawable, result.dataSource, key)
                return@withContext drawable
            }
            target?.setImageDrawable(drawable)
            return drawable
        } catch (e: Exception) {
            if (e !is CancellationException) {
                //TODO(Load Error)
            }
            Timber.e("${request.data} - ${e.message}")
            return null
        }
    }

    private fun buildKey(key: String, size: ImageSize): String {
        return if (size.isUndefined) key else "$key-${size.width},${size.height}"
    }

    private suspend fun decode(result: FetchResult, size: ImageSize): Drawable {
        return when (result) {
            is DrawableResult -> result.drawable
            is SourceResult -> decoder.decode(result.source, size, request.options)
        }
    }

    private suspend fun writeToCache(
        options: ImageRequest.Options,
        drawable: Drawable,
        source: DataSource,
        key: String
    ) {
        coroutineContext.ensureActive()
        if (options.writeCacheEnable.not()) return
        if (drawable is BitmapDrawable && key.isNotEmpty() && source != DataSource.DISK) {
            bitmapCacheManager[key] = drawable.bitmap
        }
    }
}