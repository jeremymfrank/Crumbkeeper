package com.crumbkeeper.app

import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncomingRecipeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun viewAndShareReadLocalAttachmentUris() {
        val uri = Uri.parse("content://documents/42")
        assertEquals(uri, IncomingRecipe.from(Intent(Intent.ACTION_VIEW, uri))?.uri)
        assertEquals(uri, IncomingRecipe.from(Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri)
        })?.uri)
        assertEquals(uri, IncomingRecipe.from(Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"; clipData = ClipData.newRawUri("recipe", uri)
        })?.uri)
    }

    @Test fun repeatedAttachmentGetsANewRequestAndTextSharingStillWorks() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("content://documents/42"))
        assertNotEquals(IncomingRecipe.from(intent)?.id, IncomingRecipe.from(intent)?.id)
        assertEquals("Bread", IncomingRecipe.from(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Bread")
        })?.text)
        assertNull(IncomingRecipe.from(Intent(Intent.ACTION_MAIN)))
        assertNull(IncomingRecipe.from(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/bread.crumbrecipe"))))
    }

    @Test fun androidOffersCrumbkeeperForAttachmentsAndOpaqueDocumentUris() {
        val cases = listOf(
            "content://downloads/42" to "application/octet-stream",
            "content://downloads/42" to "application/zip",
            "content://downloads/42" to "application/vnd.crumbkeeper.recipe",
            "content://downloads/42" to "application/vnd.crumbkeeper.book",
            "content://files/Bread.v1.0.0.crumbrecipe" to "text/plain",
            "file:///sdcard/Download/Bread.crumbrecipe" to null,
            "file:///sdcard/Download/Cookbook.crumbbook" to null
        )
        cases.forEach { (uri, mime) ->
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), mime)
                .setPackage(context.packageName)
            @Suppress("DEPRECATION")
            val matches = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            assertTrue("No association for $uri ($mime)", matches.any { it.activityInfo.name == MainActivity::class.java.name })
        }
    }
}
