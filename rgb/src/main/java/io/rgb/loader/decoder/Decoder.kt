package io.rgb.loader.decoder

import android.graphics.drawable.Drawable
import io.rgb.image.ImageRequest
import io.rgb.image.ImageSize
import okio.Source

/**
 * @author conghai on 27/07/2021.
 */
interface Decoder {
    suspend fun decode(source: Source, size: ImageSize, options: ImageRequest.Options): Drawable
}