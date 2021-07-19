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
    val request: Request,
) {
    class Builder(val url: String) {
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
            request = request ?: Request.Builder().url(url).build()
        ).also {
            assert(it.url.isNotEmpty())
        }
    }
}