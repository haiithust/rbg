package io.rgb.loader.decoder

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build.VERSION.SDK_INT
import androidx.annotation.Px
import androidx.core.graphics.applyCanvas
import androidx.exifinterface.media.ExifInterface
import io.rgb.image.ImageSize
import io.rgb.utils.toDrawable
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.io.InputStream
import kotlin.math.max

class BitmapFactoryDecoder(private val context: Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun decode(
        source: Source,
        size: ImageSize,
    ): Drawable {
        return BitmapFactory.Options().run {
            inJustDecodeBounds = true
            val buffer = source.buffer()
            BitmapFactory.decodeStream(buffer.peek().inputStream(), null, this)
            inJustDecodeBounds = false

            // Read the image's EXIF data.
            val isFlipped: Boolean
            val rotationDegrees: Int
            if (shouldReadExifData(outMimeType)) {
                val exifInterface = ExifInterface(ExifInterfaceInputStream(buffer.peek().inputStream()))
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
            inSampleSize = calculateInSampleSize(srcWidth, srcHeight, size.width, size.height)

            inPreferredConfig = Bitmap.Config.RGB_565
            val outBitmap = BitmapFactory.decodeStream(buffer.inputStream(), null, this)
            checkNotNull(outBitmap) {
                "Bitmap decode null from source"
            }
            applyExifTransformations(outBitmap, inPreferredConfig, isFlipped, rotationDegrees)
        }.toDrawable(context)
    }

     private fun calculateInSampleSize(
         @Px srcWidth: Int,
         @Px srcHeight: Int,
         @Px dstWidth: Int,
         @Px dstHeight: Int,
     ): Int {
         val widthInSampleSize = Integer.highestOneBit(srcWidth / dstWidth).coerceAtLeast(1)
         val heightInSampleSize = Integer.highestOneBit(srcHeight / dstHeight).coerceAtLeast(1)
         return max(widthInSampleSize, heightInSampleSize)
     }

    private fun shouldReadExifData(mimeType: String?): Boolean {
        return mimeType != null && mimeType in SUPPORTED_EXIF_MIME_TYPES
    }

    private fun applyExifTransformations(
        inBitmap: Bitmap,
        config: Bitmap.Config,
        isFlipped: Boolean,
        rotationDegrees: Int
    ): Bitmap {
        // Short circuit if there are no transformations to apply.
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
        @Volatile private var availableBytes = GIGABYTE_IN_BYTES

        override fun read() = interceptBytesRead(delegate.read())

        override fun read(b: ByteArray) = interceptBytesRead(delegate.read(b))

        override fun read(b: ByteArray, off: Int, len: Int) = interceptBytesRead(delegate.read(b, off, len))

        override fun skip(n: Long) = delegate.skip(n)

        override fun available() = availableBytes

        override fun close() = delegate.close()

        private fun interceptBytesRead(bytesRead: Int): Int {
            if (bytesRead == -1) availableBytes = 0
            return bytesRead
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
        private val SUPPORTED_EXIF_MIME_TYPES = arrayOf(MIME_TYPE_JPEG, MIME_TYPE_WEBP, MIME_TYPE_HEIC, MIME_TYPE_HEIF)
    }
}
