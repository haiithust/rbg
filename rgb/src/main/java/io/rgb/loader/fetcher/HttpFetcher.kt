package io.rgb.loader.fetcher

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.NetworkOnMainThreadException
import androidx.core.graphics.drawable.toDrawable
import io.rgb.config.PixelLog
import io.rgb.image.ImageSize
import io.rgb.utils.getDecodedBitmapFromByteArray
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.MainCoroutineDispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import kotlin.coroutines.coroutineContext

/**
 * @author conghai on 16/07/2021.
 */
class HttpFetcher(private val context: Context) : Fetcher<Uri> {
    private val okHttpClient = OkHttpClient()

    override fun handles(data: Uri): Boolean = data.scheme == "http" || data.scheme == "https"

    override fun key(data: Uri): String = data.toString()

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun fetch(data: Uri, size: ImageSize): Drawable {
        Timber.d("Starting Fetch $data")
        val request = Request.Builder().url(data.toString().toHttpUrl()).build()
        if (coroutineContext[CoroutineDispatcher] is MainCoroutineDispatcher) {
            throw NetworkOnMainThreadException()
        }
        val response: Response = okHttpClient.newCall(request).execute()
        response.use {
            if (!response.isSuccessful) {
                response.body?.close()
                throw HttpException(response)
            }
            val body = checkNotNull(response.body) { "Null response body!" }
            val bytes = body.bytes()
            val bitmap = if (size.width > 0 && size.height > 0)
                bytes.getDecodedBitmapFromByteArray(size.width, size.height)
            else bytes.getDecodedBitmapFromByteArray()
            return bitmap.toDrawable(context.resources)
        }
    }
}

class HttpException(private val response: Response) :
    RuntimeException("HTTP ${response.code}: ${response.message}")