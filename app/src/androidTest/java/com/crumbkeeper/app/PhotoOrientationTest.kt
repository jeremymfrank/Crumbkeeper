package com.crumbkeeper.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import com.crumbkeeper.app.data.RecipePhotos
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PhotoOrientationTest {
    private fun quadrants(): Bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until height) for (x in 0 until width) setPixel(x, y,
            if (y < 40) { if (x < 60) Color.RED else Color.GREEN }
            else { if (x < 60) Color.BLUE else Color.YELLOW })
    }

    @Test fun handlesEveryExifRotationAndReflection() {
        val source = quadrants()
        val expected = mapOf(
            1 to listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW),
            2 to listOf(Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE),
            3 to listOf(Color.YELLOW, Color.BLUE, Color.GREEN, Color.RED),
            4 to listOf(Color.BLUE, Color.YELLOW, Color.RED, Color.GREEN),
            5 to listOf(Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW),
            6 to listOf(Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN),
            7 to listOf(Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED),
            8 to listOf(Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE)
        )
        expected.forEach { (orientation, colors) ->
            val result = RecipePhotos.orient(source, orientation)
            assertEquals(if (orientation >= 5) 80 else 120, result.width)
            assertEquals(if (orientation >= 5) 120 else 80, result.height)
            assertEquals(colors, listOf(result.getPixel(10, 10), result.getPixel(result.width - 11, 10),
                result.getPixel(10, result.height - 11), result.getPixel(result.width - 11, result.height - 11)))
            if (result !== source) result.recycle()
        }
        source.recycle()
    }

    @Test fun readsCameraOrientationWithoutChangingOriginalAndBoundsLargeDecodes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("rotated-photo", ".jpg", context.cacheDir)
        try {
            val source = quadrants()
            file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            source.recycle()
            ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val original = file.readBytes()
            val result = RecipePhotos.load(file.absolutePath)!!
            assertEquals(80, result.width)
            assertEquals(120, result.height)
            val pixel = result.getPixel(10, 10)
            assertTrue(Color.blue(pixel) > 230 && Color.red(pixel) < 25)
            assertArrayEquals(original, file.readBytes())
            val small = RecipePhotos.load(file.absolutePath, 32)!!
            assertTrue(maxOf(small.width, small.height) <= 32)
        } finally { file.delete() }
    }
}
