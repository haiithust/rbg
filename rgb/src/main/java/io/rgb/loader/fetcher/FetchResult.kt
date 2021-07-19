package io.rgb.loader.fetcher

import android.graphics.drawable.Drawable
import io.rgb.loader.decoder.DataSource
import okio.BufferedSource

/**
 * @author conghai on 19/07/2021.
 */
sealed class FetchResult(open val dataSource: DataSource)

data class SourceResult(
    val source: BufferedSource,
    val mimeType: String?,
    override val dataSource: DataSource
) : FetchResult(dataSource)

data class DrawableResult(
    val drawable: Drawable,
    override val dataSource: DataSource
) : FetchResult(dataSource)