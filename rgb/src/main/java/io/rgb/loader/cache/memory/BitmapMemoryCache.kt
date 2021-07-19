package io.rgb.loader.cache.memory

import android.graphics.Bitmap
import android.os.Build
import android.util.LruCache
import io.rgb.config.PixelLog
import io.rgb.loader.cache.BitmapDatasource
import io.rgb.loader.cache.Priority
import timber.log.Timber


internal class BitmapMemoryCache(
    override val priority: Priority = Priority.HIGH
) : BitmapDatasource {
    /*   Get max available VM memory, exceeding this amount will throw an
       OutOfMemory exception. Stored in kilobytes as LruCache takes an
      int in its constructor.*/

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()

    // Use 1/8th of the available memory for this memory cache.
    private val maxCacheSize = maxMemory / 8

    init {
        Timber.d("Max JVM = $maxMemory")
        Timber.d("Cache Size = ${maxCacheSize / 1024} MegaBytes")
    }


    fun setCacheSize(cacheSizeInMegaBytes: Int) {
        cache.resize(cacheSizeInMegaBytes * 1024)
        Timber.d("New Cache Size = $cacheSizeInMegaBytes MegaBytes")
    }

    private val cache = object : LruCache<String, Bitmap>(maxCacheSize) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            // The cache size will be measured in kilobytes rather than
            // number of items.
            return value.byteCount / 1024
        }
    }

    override operator fun get(key: String): Bitmap? = cache[key]

    override operator fun set(key: String, bitmap: Bitmap) {
        synchronized(cache) {
            if (cache.get(key) == null) {
                cache.put(key, bitmap)
            }
        }
    }

    override fun clear(key: String) {
        cache.remove(key)
    }

    override fun clear() = cache.evictAll()
}