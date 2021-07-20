package io.rgb.loader.cache

import android.graphics.Bitmap
import androidx.annotation.WorkerThread

/**
 * @author conghai on 18/07/2021.
 */
internal class BitmapCacheManager(
    private val memoryCache: BitmapDatasource,
    private val diskCache: BitmapDatasource
) {
    @WorkerThread
    fun getMemoryCache(key: String): Bitmap? {
        return memoryCache[key]
    }

    @WorkerThread
    fun getDiskCache(key: String): Bitmap? {
        return diskCache[key]
    }

    @WorkerThread
    operator fun set(key: String, value: Bitmap) {
        if (value.isRecycled) return
        memoryCache[key] = value
        diskCache[key] = value
    }
}