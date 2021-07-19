package io.rgb.loader

import android.content.Context
import android.net.Uri
import android.widget.ImageView
import io.pixel.config.ImageRequest
import io.rgb.loader.cache.CacheManager
import io.rgb.loader.cache.disk.BitmapDiskCache
import io.rgb.loader.cache.memory.BitmapMemoryCache
import io.rgb.loader.fetcher.Fetcher
import io.rgb.loader.fetcher.HttpFetcher
import io.rgb.loader.load.ImageLoadExecution
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

class ImageLoaderManager(
    private val applicationContext: Context,
    private val dispatcher: CoroutineDispatcher
) {
    private val dataMappers = listOf(
        ResourceIntMapper(applicationContext),
        StringMapper()
    )
    private val fetcher = listOf(HttpFetcher(applicationContext))
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private val cacheManager = CacheManager(
        listOf(
            BitmapMemoryCache(),
            BitmapDiskCache(applicationContext)
        ),
    )

    fun enqueue(
        target: ImageView,
        request: ImageRequest
    ): Job {
        val mappedData = mappedData(request.url)
        val fetcher = resolveFetcher(mappedData)
        val job = scope.launch {
            ImageLoadExecution(
                target = target,
                request = request,
                fetcher = fetcher,
                uri = mappedData,
                cacheManager = cacheManager,
                dispatcher = dispatcher
            ).invoke()
        }
        target.requestManager.setCurrentRequestJob(request, job)
        if (target.isAttachedToWindowCompat.not()) {
            Timber.d("Target View not Attached to window")
            target.requestManager.onViewDetachedFromWindow(target)
        }
        return job
    }

    fun release() {
        scope.cancel()
    }

    @Suppress("UNCHECKED_CAST")
    private fun mappedData(data: Any): Uri {
        val mapper = dataMappers.find {
            it.acceptType.isAssignableFrom(data::class.java) && (it as RequestDataMapper<Any>).handles(
                data
            )
        } ?: throw IllegalArgumentException("$data not supported")
        return (mapper as RequestDataMapper<Any>).map(data)
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveFetcher(mappedData: Uri): Fetcher<Uri> {
        return fetcher.find { (it as Fetcher<Any>).handles(mappedData) }
            ?: throw IllegalArgumentException("Can not find fetcher for $mappedData not supported")
    }


    companion object {
        @Volatile
        var INSTANCE: ImageLoaderManager? = null

        fun getInstance(context: Context): ImageLoaderManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ImageLoaderManager(context.applicationContext, Dispatchers.IO).also {
                    INSTANCE = it
                }
            }
        }
    }
}