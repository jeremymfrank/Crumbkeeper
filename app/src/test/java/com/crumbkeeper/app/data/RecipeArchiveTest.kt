package com.crumbkeeper.app.data

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RecipeArchiveTest {
    @Test fun singleRecipeZipPreservesPhotoAndTimer() {
        val image = File.createTempFile("crumbkeeper", ".jpg")
        try {
            val bytes = byteArrayOf(0x12, 0x34, 0x56, 0x78)
            image.writeBytes(bytes)
            val recipe = Recipe(title = "Smashed potatoes", ingredients = listOf("1 lb potatoes"),
                notes = "Golden", coverImage = image.absolutePath, coverFraming = CoverFraming(.2f, .8f, 2f),
                steps = listOf(RecipeStep(instruction = "Bake 20 minutes",
                    timers = listOf(TimerPreset(label = "Bake", durationSeconds = 1200)))))
            val output = ByteArrayOutputStream()
            RecipeArchive.writeRecipe(recipe, output)
            val archive = RecipeArchive.prepare(ByteArrayInputStream(output.toByteArray()))
            assertFalse(archive.isBook)
            assertEquals(1, archive.recipes.size)
            assertEquals(recipe.id, archive.recipes.single().id)
            assertEquals(1200, archive.recipes.single().steps.single().timers.single().durationSeconds)
            assertArrayEquals(bytes, archive.images.values.single())
            assertEquals(recipe.coverFraming, archive.recipes.single().coverFraming)
            assertArrayEquals(bytes, image.readBytes())
            assertFalse(archive.recipes.single().coverImage!!.contains(image.parentFile!!.absolutePath))
        } finally { image.delete() }
    }

    @Test fun multiRecipeBackupPreservesDistinctIds() {
        val recipes = listOf(Recipe(title = "Bread"), Recipe(title = "Soup"))
        val output = ByteArrayOutputStream()
        RecipeArchive.writeBook(recipes, output)
        val result = RecipeArchive.prepare(ByteArrayInputStream(output.toByteArray()))
        assertTrue(result.isBook)
        assertEquals(recipes.map { it.id }, result.recipes.map { it.id })
    }

    @Test fun rejectsTraversalPaths() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("../danger.txt"))
            zip.write("danger".toByteArray())
            zip.closeEntry()
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecipeArchive.prepare(ByteArrayInputStream(output.toByteArray()))
        }
    }
}
