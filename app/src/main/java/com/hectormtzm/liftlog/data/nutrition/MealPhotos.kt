package com.hectormtzm.liftlog.data.nutrition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Stores meal photos (downscaled JPEGs) in app-private storage. */
class MealPhotos(private val context: Context) {
    private val dir get() = File(context.filesDir, "meal_photos").apply { mkdirs() }

    /** A fresh file for the camera to write into (shared through the FileProvider). */
    fun newCaptureFile(): File = File(File(context.cacheDir, "captures").apply { mkdirs() }, "capture_${UUID.randomUUID()}.jpg")

    /**
     * Reads an image (camera capture or gallery pick), fixes rotation, scales the long edge to
     * [maxDim] and saves it as JPEG. Returns the saved file and its bytes (what we send to Claude).
     */
    suspend fun importImage(uri: Uri, maxDim: Int = 1280): Pair<File, ByteArray> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("Could not open image")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Could not decode image")
        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scale = minOf(1f, maxDim.toFloat() / maxOf(decoded.width, decoded.height))
        val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
        val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        val bytes = ByteArrayOutputStream().use { bos ->
            out.compress(Bitmap.CompressFormat.JPEG, 85, bos); bos.toByteArray()
        }
        val file = File(dir, "${UUID.randomUUID()}.jpg").apply { writeBytes(bytes) }
        if (out !== decoded) out.recycle()
        decoded.recycle()
        file to bytes
    }

    fun delete(path: String?) {
        if (path != null) runCatching { File(path).delete() }
    }

    fun read(path: String?): ByteArray? = path?.let { p -> runCatching { File(p).readBytes() }.getOrNull() }
}
