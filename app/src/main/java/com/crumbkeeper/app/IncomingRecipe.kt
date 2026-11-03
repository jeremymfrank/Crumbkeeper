package com.crumbkeeper.app

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import java.util.UUID

/** A unique request also lets the same attachment be opened twice in a warm app. */
data class IncomingRecipe(
    val id: String = UUID.randomUUID().toString(),
    val text: String? = null,
    val uri: Uri? = null
) {
    companion object {
        fun from(intent: Intent?): IncomingRecipe? {
            if (intent == null) return null
            val uri = when (intent.action) {
                Intent.ACTION_VIEW -> intent.data
                Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                else -> return null
            }
            if (uri != null) {
                // The importer reads local attachments; it does not fetch web links.
                return uri.takeIf { it.scheme in setOf("content", "file") }?.let { IncomingRecipe(uri = it) }
            }
            return intent.takeIf { it.action == Intent.ACTION_SEND && it.type == "text/plain" }
                ?.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { IncomingRecipe(text = it) }
        }
    }
}
