package com.crumbkeeper.app.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.crumbkeeper.app.data.Recipe
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class GildedLibraryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun libraryDisplaysPhotosAndAddRecipeMenu() = checkLibrary(1f)
    @Test fun libraryRemainsReadableWithLargeText() = checkLibrary(2f)

    private fun checkLibrary(scale: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("recipe-cover", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(158, 111, 65))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val recipes = listOf(
            Recipe(id = "bread", title = "Jawatech’s Gluten-Free Bread", servings = "1 loaf",
                ingredients = List(7) { "Ingredient" }, coverImage = file.absolutePath),
            Recipe(id = "chicken", title = "Baked Chicken Breasts"),
            Recipe(id = "potatoes", title = "Crispy Smashed Golden Potatoes")
        )
        var added = 0
        try {
            compose.setContent {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                    GildedTheme { Surface {
                        LibraryScreen(recipes, false, { added++ }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
                    } }
                }
            }
            compose.onNodeWithTag("recipe-library").performScrollToNode(hasTestTag("recipe-card-bread"))
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription("Recipe cover: Jawatech’s Gluten-Free Bread").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("cover-bread").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription("Full photo: Jawatech’s Gluten-Free Bread").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Close photo").performClick()
            compose.onNodeWithTag("recipe-library").performScrollToNode(hasText("Add Recipe"))
            val directory = context.getExternalFilesDir("visual-checks")!!.apply { mkdirs() }
            File(directory, "gilded-library-${scale}.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithText("Add Recipe").performClick()
            compose.onNodeWithText("New Recipe").assertIsDisplayed().performClick()
            assertEquals(1, added)
        } finally { file.delete() }
    }
}
