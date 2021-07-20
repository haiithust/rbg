package io.rgb.image

import android.graphics.drawable.Drawable
import okhttp3.Request

/**
 * PixelOptions allow to customize each load.
 * @author Mobin Munir
 */
enum class ImageFormat {
    PNG
}

class ImageRequest private constructor(
    val url: String,
    val width: Int,
    val height: Int,
    val placeHolder: Drawable?,
    val httpRequest: Request,
    val options: Options = Options.EMPTY
) {
    class Options(
        val readFromCacheEnable: Boolean,
        val writeCacheEnable: Boolean,
    ) {
        companion object {
            val EMPTY = Options(
                readFromCacheEnable = true,
                writeCacheEnable = true,
            )
        }
    }

    class Builder(private val url: String) {
        private var width = 0
        private var height = 0
        private var placeHolder: Drawable? = null
        private var imageFormat = ImageFormat.PNG
        private var request: Request? = null

        fun setPlaceHolder(placeHolderResource: Drawable?) {
            this.placeHolder = placeHolderResource
        }

        fun setImageSize(width: Int, height: Int) {
            this.width = width
            this.height = height
        }

        fun setImageFormat(imageFormat: ImageFormat) {
            this.imageFormat = imageFormat
        }

        fun setRequest(request: Request) {
            this.request = request
        }

        fun build() = ImageRequest(
            url = url,
            width = width,
            height = height,
            placeHolder = placeHolder,
            httpRequest = request ?: Request.Builder().url(url).build()
        ).also {
            assert(it.url.isNotEmpty())
        }
    }
}