package io.rgb.image

/**
 * @author conghai on 14/07/2021.
 */
class ImageSize(val width: Int, val height: Int) {
    val isUndefined: Boolean
        get() = width == 0 || height == 0
}

    val ImageRequest.size: ImageSize
    get() = ImageSize(width, height)