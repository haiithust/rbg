package io.rgb

import android.graphics.drawable.Drawable
import android.widget.ImageView
import io.rgb.image.ImageRequest
import kotlinx.coroutines.Job

/**
 * @author conghai on 27/07/2021.
 */
interface ImageLoader {
    fun cancel()
    fun clearMemory()
    fun enqueue(target: ImageView, request: ImageRequest): Job
    suspend fun execute(target: ImageView? = null, request: ImageRequest): Drawable?
}