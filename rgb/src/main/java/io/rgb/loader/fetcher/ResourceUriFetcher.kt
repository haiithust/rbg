package io.rgb.loader.fetcher

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.TypedValue
import android.webkit.MimeTypeMap
import androidx.appcompat.content.res.AppCompatResources
import io.rgb.image.ImageSize
import io.rgb.loader.decoder.DataSource
import io.rgb.utils.getMimeTypeFromUrl
import io.rgb.utils.nightMode
import okio.buffer
import okio.source

internal class ResourceUriFetcher(
    private val context: Context,
) : Fetcher<Uri> {

    override fun handles(data: Uri) = data.scheme == ContentResolver.SCHEME_ANDROID_RESOURCE

    override fun key(data: Uri) = "$data-${context.resources.configuration.nightMode}"

    override suspend fun fetch(
        data: Uri,
        size: ImageSize,
    ): FetchResult {
        // Expected format: android.resource://example.package.name/12345678
        val packageName =
            data.authority?.takeIf { it.isNotBlank() } ?: throwInvalidUriException(data)
        val resId = data.pathSegments.lastOrNull()?.toIntOrNull() ?: throwInvalidUriException(data)

        val resources = context.packageManager.getResourcesForApplication(packageName)
        val path = TypedValue().apply { resources.getValue(resId, this, true) }.string
        val entryName = path.substring(path.lastIndexOf('/'))
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromUrl(entryName)

        return if (mimeType == MIME_TYPE_XML) {
            val drawable = AppCompatResources.getDrawable(context, resId) ?: throwInvalidUriException(data)
            DrawableResult(drawable, DataSource.DISK)
        } else {
            SourceResult(
                source = resources.openRawResource(resId).source().buffer(),
                mimeType = mimeType,
                dataSource = DataSource.DISK
            )
        }
    }

    private fun throwInvalidUriException(data: Uri): Nothing {
        throw IllegalStateException("Invalid ${ContentResolver.SCHEME_ANDROID_RESOURCE} URI: $data")
    }

    companion object {
        private const val MIME_TYPE_XML = "text/xml"
    }
}
