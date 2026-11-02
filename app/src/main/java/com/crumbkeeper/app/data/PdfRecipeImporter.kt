package com.crumbkeeper.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF import via Android PdfRenderer and the locally bundled ML Kit OCR model.
 * Does not interpret raw PDF object bytes as text or send data to a server.
 */
object PdfRecipeImporter {
    private const val MAX_PDF_BYTES = 25L * 1024L * 1024L
    private const val MAX_PAGES = 40
    private const val MAX_OCR_CHARS = 180_000

    data class Result(val parsed: ParsedRecipe, val pages: Int)

    suspend fun import(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val local = File.createTempFile("crumbkeeper-import-", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { source ->
                local.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= MAX_PDF_BYTES) { "PDF is too large (25 MB maximum)." }
                        output.write(buffer, 0, n)
                    }
                }
            } ?: error("Cannot open the selected PDF.")
            require(local.length() >= 5) { "The selected PDF is empty." }
            require(local.inputStream().use {
                val signature = ByteArray(5)
                it.read(signature) == 5 && signature.contentEquals("%PDF-".toByteArray(Charsets.US_ASCII))
            }) { "This file does not contain a valid PDF header." }

            val extracted = StringBuilder()
            var pageCount = 0
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                ParcelFileDescriptor.open(local, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        pageCount = renderer.pageCount
                        require(pageCount in 1..MAX_PAGES) {
                            "PDF has $pageCount pages. Import files up to $MAX_PAGES pages."
                        }
                        for (index in 0 until pageCount) {
                            renderer.openPage(index).use { page ->
                                // OCR-friendly resolution, bounded for phone memory.
                                val scale = minOf(2.0f, 2100f / page.width, 2500f / page.height)
                                val width = (page.width * scale).toInt().coerceAtLeast(1)
                                val height = (page.height * scale).toInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                try {
                                    bitmap.eraseColor(Color.WHITE)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    val read = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
                                    if (read.text.isNotBlank()) extracted.appendLine(read.text)
                                    require(extracted.length <= MAX_OCR_CHARS) {
                                        "PDF contains too much text for a recipe import."
                                    }
                                } finally { bitmap.recycle() }
                            }
                        }
                    }
                }
            } finally { recognizer.close() }
            val ocrText = extracted.toString().trim()
            require(ocrText.length >= 25) {
                "No readable text could be detected in this PDF. Try a clearer PDF."
            }
            val cleaned = PdfRecipeText.clean(ocrText)
            val draft = RecipeParser.parse(cleaned.text)
            Result(ParsedRecipe(
                recipe = draft.recipe.copy(originalText = ocrText),
                warnings = (listOf(
                    "Imported $pageCount PDF page(s) offline. Review fractions, quantities, temperatures and suggested timers: OCR may misread text."
                ) + cleaned.warnings + draft.warnings).distinct(),
                unplaced = draft.unplaced.take(30)
            ), pageCount)
        } finally { local.delete() }
    }
}
