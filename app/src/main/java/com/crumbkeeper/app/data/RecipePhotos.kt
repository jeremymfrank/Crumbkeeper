package com.crumbkeeper.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Bounded, off-main-thread decoding, including all eight EXIF rotations/reflections. */
object RecipePhotos {
    private const val MAX_FILE_BYTES = 12L * 1024 * 1024
    private val cache = object : LruCache<String, Bitmap>(
        minOf(24 * 1024 * 1024L, Runtime.getRuntime().maxMemory() / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    suspend fun importPhoto(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "recipe-images").apply { mkdirs() }
        val target = File(directory, "${UUID.randomUUID()}.img")
        try {
            val source = context.contentResolver.openInputStream(uri) ?: error("Could not read photo.")
            source.use { input -> target.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_FILE_BYTES) { "Choose a photo under 12 MB so it can be shared and backed up." }
                    output.write(buffer, 0, count)
                }
            } }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This image format could not be opened. Try a JPEG or PNG photo." }
            target.absolutePath
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    suspend fun load(path: String, maxEdge: Int = 1024): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(path)
        val key = "$path:${file.lastModified()}:${file.length()}:$maxEdge"
        cache.get(key)?.let { return@withContext it }
        runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return@withContext null
            var sample = 1
            while (maxOf(options.outWidth, options.outHeight) / sample > maxEdge.coerceAtLeast(1)) sample *= 2
            options.inJustDecodeBounds = false
            options.inSampleSize = sample
            val bitmap = BitmapFactory.decodeFile(path, options) ?: return@withContext null
            val orientation = runCatching { ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            ) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val upright = orient(bitmap, orientation)
            if (upright !== bitmap) bitmap.recycle()
            cache.put(key, upright)
            upright
        }.getOrNull()
    }

    internal fun orient(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> matrix.setValues(floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> matrix.setValues(floatArrayOf(0f, -1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f))
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
