package io.rgb.loader.fetcher

import android.net.Uri
import android.os.NetworkOnMainThreadException
import android.webkit.MimeTypeMap
import io.rgb.image.ImageSize
import io.rgb.loader.decoder.DataSource
import io.rgb.utils.getMimeTypeFromUrl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.MainCoroutineDispatcher
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import timber.log.Timber
import kotlin.coroutines.coroutineContext

/**
 * @author conghai on 16/07/2021.
 */
class HttpFetcher : Fetcher<Uri> {
    private val okHttpClient = OkHttpClient()

    override fun handles(data: Uri): Boolean = data.scheme == "http" || data.scheme == "https"

    override fun key(data: Uri): String = data.toString()

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun fetch(data: Uri, size: ImageSize): FetchResult {
        Timber.d("Starting Fetch $data")
        val url = data.toString().toHttpUrl()
        val request = Request.Builder().url(url).build()
        if (coroutineContext[CoroutineDispatcher] is MainCoroutineDispatcher) {
            throw NetworkOnMainThreadException()
        }
        val response: Response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            response.body?.close()
            throw HttpException(response)
        }
        val body = checkNotNull(response.body) { "Null response body!" }
        return SourceResult(
            source = body.source(),
            mimeType = getMimeType(url, body),
            dataSource = DataSource.NETWORK
        )
    }

    private fun getMimeType(data: HttpUrl, body: ResponseBody): String? {
        val rawContentType = body.contentType()?.toString()
        if (rawContentType == null || rawContentType.startsWith(MIME_TYPE_TEXT_PLAIN)) {
            MimeTypeMap.getSingleton().getMimeTypeFromUrl(data.toString())?.let { return it }
        }
        return rawContentType?.substringBefore(';')
    }

    companion object {
        private const val MIME_TYPE_TEXT_PLAIN = "text/plain"
    }
}

class HttpException(response: Response) :
    RuntimeException("HTTP ${response.code}: ${response.message}")