package com.ceyinfo.cerpstores.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Two-pass JPEG compressor:
 *   1. Decode bounds → calculate sample size that lands the bitmap close
 *      to MAX_*. This avoids loading a 12MP camera shot fully into memory
 *      just to throw 90% of it away.
 *   2. Decode at that sample size, then `createScaledBitmap` to land
 *      exactly within MAX_WIDTH × MAX_HEIGHT, write JPEG at QUALITY.
 *
 * Output goes to `cacheDir/compressed/photo_<ts>.jpg`. The backend
 * re-encodes via sharp so even if we miss the size target the server
 * normalises orientation + dimensions before OSS upload.
 *
 * Ported from cerp-mobile-app/util/ImageCompressor.kt — keep them in
 * sync if either set changes meaningfully.
 */
object ImageCompressor {

    private const val MAX_WIDTH = 1920
    private const val MAX_HEIGHT = 1920
    private const val QUALITY = 92

    fun compress(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open URI")

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight)
        options.inJustDecodeBounds = false

        val input2 = context.contentResolver.openInputStream(uri)!!
        val bitmap = BitmapFactory.decodeStream(input2, null, options)!!
        input2.close()

        val scaled = scaleBitmap(bitmap)

        val dir = File(context.cacheDir, "compressed").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")

        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        }

        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()

        return file
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        var w = width
        var h = height
        while (w > MAX_WIDTH * 2 || h > MAX_HEIGHT * 2) {
            sampleSize *= 2
            w /= 2
            h /= 2
        }
        return sampleSize
    }

    private fun scaleBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= MAX_WIDTH && height <= MAX_HEIGHT) return bitmap

        val ratio = minOf(MAX_WIDTH.toFloat() / width, MAX_HEIGHT.toFloat() / height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
