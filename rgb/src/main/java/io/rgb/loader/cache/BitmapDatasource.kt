package io.rgb.loader.cache

import android.graphics.Bitmap

/**
 * @author conghai on 18/07/2021.
 */
interface BitmapDatasource {
    operator fun get(key: String): Bitmap?
    operator fun set(key: String, bitmap: Bitmap)
    fun clear(key: String)
    fun clearMemory()
}