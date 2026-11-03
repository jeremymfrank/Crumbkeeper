package com.crumbkeeper.app

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.crumbkeeper.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class BackupRestoreTest {
    @Test fun restorePreservesPhotoFramingAndMakesAllTimersReady() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = RecipeStore(context)
        val id = "backup-${UUID.randomUUID()}"
        val image = File.createTempFile("backup-photo", ".png", context.cacheDir)
        try {
            val bitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888)
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val original = Recipe(id = id, title = "Backup bread", coverImage = image.absolutePath,
                coverFraming = CoverFraming(.2f, .8f, 1.5f), steps = listOf(RecipeStep(instruction = "Rise, then bake.",
                    timers = listOf(TimerPreset(label = "Rise", durationSeconds = 2700), TimerPreset(label = "Bake", durationSeconds = 1800)))))
            val output = ByteArrayOutputStream()
            RecipeArchive.writeBook(listOf(original), output)
            val prepared = RecipeArchive.prepare(ByteArrayInputStream(output.toByteArray()))
            val result = RecipeArchive.restore(context, store, prepared)
            assertEquals(1, result.imported)
            val restored = store.all().single { it.id == id }
            assertEquals(original.coverFraming, restored.coverFraming)
            assertEquals(original.steps, restored.steps)
            assertNotEquals(image.absolutePath, restored.coverImage)
            assertArrayEquals(image.readBytes(), File(restored.coverImage!!).readBytes())
            val timers = restored.steps.flatMap { it.timers }.map { TimerRun(it.id, it.durationSeconds.toLong() * 1000) }
            assertEquals(2, timers.size)
            assertTrue(timers.all { it.state == "ready" && it.deadlineMs == 0L })
            val repeat = RecipeArchive.restore(context, store, prepared)
            assertEquals(0, repeat.imported)
            assertEquals(1, repeat.skipped)
        } finally {
            store.moveToTrash(id)
            store.deletePermanently(id, confirmed = true)
            store.close()
            image.delete()
        }
    }
}
