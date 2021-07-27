package io.rgb.loader.decoder

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.annotation.Px
import androidx.core.content.getSystemService
import androidx.core.graphics.applyCanvas
import androidx.exifinterface.media.ExifInterface
import io.rgb.image.ImageRequest
import io.rgb.image.ImageSize
import io.rgb.utils.toDrawable
import kotlinx.coroutines.ensureActive
import okhttp3.internal.closeQuietly
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.io.InputStream
import kotlin.coroutines.coroutineContext


class BitmapFactoryDecoder(private val context: Context) : Decoder {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val screenWidth: Int
    private val screenHeight: Int

    init {
        val windowManager = context.getSystemService<WindowManager>()!!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics: WindowMetrics = windowManager.currentWindowMetrics
            val insets = windowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            screenWidth = windowMetrics.bounds.width() - insets.left - insets.right
            screenHeight = windowMetrics.bounds.height() - insets.bottom - insets.top
        } else {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }
    }

    override suspend fun decode(
        source: Source,
        size: ImageSize,
        options: ImageRequest.Options
    ): Drawable {
        return try {
            coroutineContext.ensureActive()
            BitmapFactory.Options().run {
                inJustDecodeBounds = true
                val safeSource = ExceptionCatchingSource(source)
                val safeBufferedSource = safeSource.buffer()
                BitmapFactory.decodeStream(safeBufferedSource.peek().inputStream(), null, this)
                inJustDecodeBounds = false

                // Read the image's EXIF data.
                val isFlipped: Boolean
                val rotationDegrees: Int
                if (shouldReadExifData(outMimeType)) {
                    val exifInterface =
                        ExifInterface(
                            ExifInterfaceInputStream(
                                safeBufferedSource.peek().inputStream()
                            )
                        )
                    isFlipped = exifInterface.isFlipped
                    rotationDegrees = exifInterface.rotationDegrees
                } else {
                    isFlipped = false
                    rotationDegrees = 0
                }

                // srcWidth and srcHeight are the dimensions of the image after EXIF transformations (but before sampling).
                val isSwapped = rotationDegrees == 90 || rotationDegrees == 270
                val srcWidth = if (isSwapped) outHeight else outWidth
                val srcHeight = if (isSwapped) outWidth else outHeight

                // Calculate inSampleSize
                inSampleSize = if (!size.isUndefined) {
                    calculateInSampleSize(srcWidth, srcHeight, size.width, size.height)
                } else {
                    calculateInSampleSize(srcWidth, srcHeight, screenWidth, screenHeight)
                }

                inPreferredConfig = Bitmap.Config.RGB_565
                val outBitmap = safeBufferedSource.use {
                    if (outMimeType == null) {
                        val bytes = it.readByteArray()
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
                    } else {
                        BitmapFactory.decodeStream(it.inputStream(), null, this)
                    }
                }
                checkNotNull(outBitmap) {
                    "Bitmap decode null from source"
                }
                applyExifTransformations(outBitmap, inPreferredConfig, isFlipped, rotationDegrees)
            }.toDrawable(context)
        } finally {
            source.closeQuietly()
        }
    }

    private fun calculateInSampleSize(
        @Px srcWidth: Int,
        @Px srcHeight: Int,
        @Px reqWidth: Int,
        @Px reqHeight: Int,
    ): Int {
        // Raw height and width of image
        var inSampleSize = 1
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            val halfHeight: Int = srcHeight / 2
            val halfWidth: Int = srcWidth / 2
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    private fun shouldReadExifData(mimeType: String?): Boolean {
        return mimeType != null && mimeType in SUPPORTED_EXIF_MIME_TYPES
    }

    private suspend fun applyExifTransformations(
        inBitmap: Bitmap,
        config: Bitmap.Config,
        isFlipped: Boolean,
        rotationDegrees: Int
    ): Bitmap {
        coroutineContext.ensureActive()
        val isRotated = rotationDegrees > 0
        if (!isFlipped && !isRotated) {
            return inBitmap
        }

        val matrix = Matrix()
        val centerX = inBitmap.width / 2f
        val centerY = inBitmap.height / 2f
        if (isFlipped) {
            matrix.postScale(-1f, 1f, centerX, centerY)
        }
        if (isRotated) {
            matrix.postRotate(rotationDegrees.toFloat(), centerX, centerY)
        }

        val rect = RectF(0f, 0f, inBitmap.width.toFloat(), inBitmap.height.toFloat())
        matrix.mapRect(rect)
        if (rect.left != 0f || rect.top != 0f) {
            matrix.postTranslate(-rect.left, -rect.top)
        }

        val outBitmap = if (rotationDegrees == 90 || rotationDegrees == 270) {
            Bitmap.createBitmap(inBitmap.height, inBitmap.width, config)
        } else {
            Bitmap.createBitmap(inBitmap.width, inBitmap.height, config)
        }

        outBitmap.applyCanvas {
            drawBitmap(inBitmap, matrix, paint)
        }
        return outBitmap
    }

    /** Wrap [delegate] so that it works with [ExifInterface]. */
    private class ExifInterfaceInputStream(private val delegate: InputStream) : InputStream() {

        // Ensure that this value is always larger than the size of the image
        // so ExifInterface won't stop reading the stream prematurely.
        @Volatile
        private var availableBytes = GIGABYTE_IN_BYTES

        override fun read() = interceptBytesRead(delegate.read())

        override fun read(b: ByteArray) = interceptBytesRead(delegate.read(b))

        override fun read(b: ByteArray, off: Int, len: Int) =
            interceptBytesRead(delegate.read(b, off, len))

        override fun skip(n: Long) = delegate.skip(n)

        override fun available() = availableBytes

        override fun close() = delegate.close()

        private fun interceptBytesRead(bytesRead: Int): Int {
            if (bytesRead == -1) availableBytes = 0
            return bytesRead
        }
    }

    private class ExceptionCatchingSource(delegate: Source) : ForwardingSource(delegate) {

        var exception: Exception? = null
            private set

        override fun read(sink: Buffer, byteCount: Long): Long {
            try {
                return super.read(sink, byteCount)
            } catch (e: Exception) {
                exception = e
                throw e
            }
        }
    }

    companion object {
        private const val MIME_TYPE_JPEG = "image/jpeg"
        private const val MIME_TYPE_WEBP = "image/webp"
        private const val MIME_TYPE_HEIC = "image/heic"
        private const val MIME_TYPE_HEIF = "image/heif"
        private const val GIGABYTE_IN_BYTES = 1024 * 1024 * 1024

        // NOTE: We don't support PNG EXIF data as it's very rarely used and requires buffering
        // the entire file into memory. All of the supported formats short circuit when the EXIF
        // chunk is found (often near the top of the file).
        private val SUPPORTED_EXIF_MIME_TYPES =
            arrayOf(MIME_TYPE_JPEG, MIME_TYPE_WEBP, MIME_TYPE_HEIC, MIME_TYPE_HEIF)
    }
}
