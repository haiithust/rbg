package io.rgb.loader.cache.disk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import com.jakewharton.disklrucache.DiskLruCache
import io.rgb.loader.cache.BitmapDatasource
import io.rgb.loader.cache.Priority
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest


/**
 * @author conghai on 18/07/2021.
 */
class BitmapDiskCache(
    private val context: Context,
    override val priority: Priority = Priority.LOW
) : BitmapDatasource {
    private val cache: DiskLruCache by lazy {
        DiskLruCache.open(
            getDiskCacheDir(context), 1, 1, DISK_CACHE_SIZE
        )
    }

    override fun get(key: String): Bitmap? {
        return runCatching {
            val snapshot = cache[hash(key)] ?: return@runCatching null
            return@runCatching BitmapFactory.decodeStream(snapshot.getInputStream(0))
        }.getOrNull()
    }

    override fun set(key: String, bitmap: Bitmap) {
        synchronized(cache) {
            val editor = cache.edit(hash(key)) ?: return
            try {
                val outStream = editor.newOutputStream(0)
                if (bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream)) {
                    editor.commit()
                } else editor.abort()
            } catch (e: Exception) {
                editor.abort()
            } finally {
                cache.flush()
            }
        }
    }

    override fun clear(key: String) {
        cache.remove(hash(key))
    }

    override fun clear() {
        cache.delete()
    }

    private fun getDiskCacheDir(context: Context): File {
        val cachePath =
            if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()
                || !Environment.isExternalStorageRemovable()
            ) {
                context.externalCacheDir!!.path
            } else {
                context.cacheDir.path
            }

        return File(cachePath + File.separator + DISK_CACHE_SUB_DIR)
    }

    private fun hash(s: String): String {
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(s.toByteArray())).toString(16).padStart(32, '0')
    }

    companion object {
        private const val DISK_CACHE_SIZE = 1024L * 1024L * 100 // 100MB
        private const val DISK_CACHE_SUB_DIR = "thumbnails"
    }
}