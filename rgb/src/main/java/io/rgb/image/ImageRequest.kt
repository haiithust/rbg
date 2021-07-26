package io.rgb.image

import android.graphics.drawable.Drawable

/**
 * PixelOptions allow to customize each load.
 * @author conghai on 16/07/2021.
 */
class ImageRequest private constructor(
    val data: Any,
    val width: Int,
    val height: Int,
    val placeHolder: Drawable?,
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

    class Builder(private val data: Any) {
        private var width = 0
        private var height = 0
        private var placeHolder: Drawable? = null

        fun setPlaceHolder(placeHolderResource: Drawable?) {
            this.placeHolder = placeHolderResource
        }

        fun setImageSize(width: Int, height: Int) {
            this.width = width
            this.height = height
        }

        fun build() = ImageRequest(
            data = data,
            width = width,
            height = height,
            placeHolder = placeHolder,
        )
    }
}