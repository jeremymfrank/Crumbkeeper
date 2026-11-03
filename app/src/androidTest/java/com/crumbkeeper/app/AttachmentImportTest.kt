package com.crumbkeeper.app

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.crumbkeeper.app.data.Recipe
import com.crumbkeeper.app.data.RecipeArchive
import com.crumbkeeper.app.data.RecipeStep
import com.crumbkeeper.app.data.RecipeStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AttachmentImportTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before fun clearTestInstallation() {
        context.deleteDatabase("crumbkeeper.db")
        context.getSharedPreferences("cook-session", 0).edit().clear().commit()
        context.getSharedPreferences("crumb-settings", 0).edit().clear().commit()
    }

    private fun attachment(title: String): Intent {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, "Attachment.crumbrecipe")
        val recipe = Recipe(title = title, ingredients = listOf("300 g flour"),
            steps = listOf(RecipeStep(instruction = "Mix the ingredients.")))
        file.outputStream().use { RecipeArchive.writeRecipe(recipe, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).setClass(context, MainActivity::class.java)
            .setDataAndType(uri, RecipeArchive.MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test fun coldLaunchValidatesAttachmentBeforeSaving() {
        // ActivityScenario matches lifecycle callbacks against the launch Intent.
        // MainActivity consumes/replaces that Intent, so manage lifecycle directly.
        val activity = instrumentation.startActivitySync(attachment("Cold attachment")) as MainActivity
        try {
            compose.onNodeWithText("Import recipe?").assertIsDisplayed()
            assertTrue(RecipeStore(context).all().isEmpty())
            compose.onNodeWithText("Import Missing").performClick()
            assertEquals("Cold attachment", RecipeStore(context).all().single().title)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
        }
    }

    @Test fun warmAppCanReopenTheSameAttachmentAfterCancelling() {
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            val intent = attachment("Warm attachment").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            instrumentation.runOnMainSync { activity.startActivity(intent) }
            compose.onNodeWithText("Import recipe?").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            instrumentation.runOnMainSync { activity.startActivity(intent) }
            compose.onNodeWithText("Import recipe?").assertIsDisplayed()
            instrumentation.runOnMainSync {
                val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                assertSame(activity, resumed.single { it is MainActivity })
            }
            compose.onNodeWithText("Import Missing").performClick()
            assertEquals("Warm attachment", RecipeStore(context).all().single().title)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
        }
    }
}
