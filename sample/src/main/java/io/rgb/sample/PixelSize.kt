package io.rgb.sample

import androidx.annotation.Px

/**
 * @author conghai on 13/07/2021.
 */
data class PixelSize(
    @Px val width: Int,
    @Px val height: Int
)  {
    init {
        require(width > 0 && height > 0) { "width and height must be > 0." }
    }
}
