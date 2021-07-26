package io.rgb.loader.fetcher

import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.net.toFile
import io.rgb.image.ImageSize
import io.rgb.loader.decoder.DataSource
import okio.buffer
import okio.source

object FileFetcher : Fetcher<Uri> {

    override fun handles(data: Uri): Boolean = data.scheme == "file"

    override fun key(data: Uri): String = data.toString()

    override suspend fun fetch(data: Uri, size: ImageSize): FetchResult {
        val file = data.toFile()
        return SourceResult(
            source = file.source().buffer(),
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension),
            dataSource = DataSource.DISK
        )
    }
}
