package io.rgb.loader.cache

import android.graphics.Bitmap
import androidx.annotation.WorkerThread
import io.rgb.loader.cache.memory.BitmapMemoryCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * @author conghai on 18/07/2021.
 */
internal class CacheManager(
    private val memoryCaches: List<BitmapDatasource>,
) {
    @WorkerThread
    operator fun get(key: String): Bitmap? {
        memoryCaches.forEach { dataSource ->
            dataSource[key]?.let {

                    bitmap ->
                return bitmap
            }
        }
        return null
    }

    @WorkerThread
    operator fun set(key: String, value: Bitmap) {
        memoryCaches.forEach { dataSource ->
            dataSource[key] = value
        }
    }
}