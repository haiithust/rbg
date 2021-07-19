package io.rgb.loader.cache

import android.graphics.Bitmap

/**
 * @author conghai on 18/07/2021.
 */
interface BitmapDatasource {
    val priority: Priority
    operator fun get(key: String): Bitmap?
    operator fun set(key: String, bitmap: Bitmap)
    fun clear(key: String)
    fun clear()
}

enum class Priority(val value: Int) {
    LOW(1), HIGH(2)
}