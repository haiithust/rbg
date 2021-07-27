package io.rgb.loader

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import io.rgb.ImageLoader
import io.rgb.image.ImageRequest
import io.rgb.loader.cache.BitmapCacheManager
import io.rgb.loader.cache.disk.BitmapDiskCache
import io.rgb.loader.cache.memory.BitmapMemoryCache
import io.rgb.loader.decoder.BitmapFactoryDecoder
import io.rgb.loader.fetcher.Fetcher
import io.rgb.loader.fetcher.FileFetcher
import io.rgb.loader.fetcher.HttpFetcher
import io.rgb.loader.fetcher.ResourceUriFetcher
import io.rgb.loader.load.ImageLoadTask
import io.rgb.loader.mapper.RequestDataMapper
import io.rgb.loader.mapper.ResourceIntMapper
import io.rgb.loader.mapper.StringMapper
import io.rgb.utils.isAttachedToWindowCompat
import io.rgb.utils.requestManager
import kotlinx.coroutines.*
import timber.log.Timber

/**
 * A proxy class representing a link to load process and it's states.
 */

internal class ImageLoaderImpl(
    private val applicationContext: Context,
    private val dispatcher: CoroutineDispatcher
) : ImageLoader {
    private val dataMappers = listOf(
        ResourceIntMapper(applicationContext),
        StringMapper(),
    )
    private val fetcher = listOf(
        HttpFetcher,
        FileFetcher,
        ResourceUriFetcher(applicationContext)
    )
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private val cacheManager = BitmapCacheManager(
        BitmapMemoryCache(),
        BitmapDiskCache(applicationContext)
    )
    private val decoder = BitmapFactoryDecoder(applicationContext)

    override fun enqueue(
        target: ImageView,
        request: ImageRequest
    ): Job {
        val mappedData = if (request.data is Uri) request.data else mappedData(request.data)
        val fetcher = resolveFetcher(mappedData)
        val job = scope.launch {
            ImageLoadTask(
                context = applicationContext,
                target = target,
                request = request,
                fetcher = fetcher,
                uri = mappedData,
                bitmapCacheManager = cacheManager,
                dispatcher = dispatcher,
                decoder = decoder
            ).execute()
        }
        target.requestManager.setCurrentRequestJob(request, job)
        if (target.isAttachedToWindowCompat.not()) {
            Timber.d("Target View not Attached to window")
            target.requestManager.onViewDetachedFromWindow(target)
        }
        return job
    }

    override suspend fun execute(
        target: ImageView?,
        request: ImageRequest
    ): Drawable? {
        val mappedData = if (request.data is Uri) request.data else mappedData(request.data)
        val fetcher = resolveFetcher(mappedData)
        return ImageLoadTask(
            context = applicationContext,
            target = target,
            request = request,
            fetcher = fetcher,
            uri = mappedData,
            bitmapCacheManager = cacheManager,
            dispatcher = dispatcher,
            decoder = decoder
        ).execute()
    }

    override fun cancel() {
        scope.cancel()
    }

    override fun clearMemory() {
        cacheManager.clearMemory()
    }

    @Suppress("UNCHECKED_CAST")
    private fun mappedData(data: Any): Uri {
        dataMappers.forEach {
            if (it.acceptType.isAssignableFrom(data::class.java)
                && (it as RequestDataMapper<Any>).handles(data)
            ) {
                return it.map(data)
            }
        }
        throw IllegalArgumentException("Can not handle $data")
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveFetcher(mappedData: Uri): Fetcher<Uri> {
        return fetcher.find { (it as Fetcher<Any>).handles(mappedData) }
            ?: throw IllegalArgumentException("Can not find fetcher for $mappedData not supported")
    }


    companion object {
        @Volatile
        var INSTANCE: ImageLoaderImpl? = null

        fun getInstance(
            context: Context,
            dispatcher: CoroutineDispatcher = Dispatchers.IO
        ): ImageLoaderImpl {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ImageLoaderImpl(context.applicationContext, dispatcher).also {
                    INSTANCE = it
                }
            }
        }
    }
}