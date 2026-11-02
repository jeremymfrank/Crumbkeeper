package com.crumbkeeper.app.data

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Avoid treating arbitrary binary file bytes as a recipe text document. */
object RecipeFileSniffer {
    enum class Kind { PDF, ARCHIVE, TEXT }
    fun kind(header: ByteArray, mime: String? = null): Kind {
        if (header.size >= 5 && header.take(5).toByteArray()
                .contentEquals("%PDF-".toByteArray(Charsets.US_ASCII))) return Kind.PDF
        if (header.size >= 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte())
            return Kind.ARCHIVE
        if (mime.equals("application/pdf", ignoreCase = true)) return Kind.PDF
        if (header.any { it == 0.toByte() } ||
            header.count { it == 0xFF.toByte() || it == 0xFE.toByte() } > 2)
            throw IllegalArgumentException("This file is binary, not recipe text.")
        return Kind.TEXT
    }

    fun decodeText(bytes: ByteArray): String {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try { decoder.decode(ByteBuffer.wrap(bytes)).toString() }
            catch (e: Exception) { throw IllegalArgumentException("This file is not valid UTF-8 recipe text.", e) }
        require(text.none { it == '\u0000' }) { "Binary data cannot be imported as recipe text." }
        val bad = text.count { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }
        require(bad < 5) { "File contains binary data instead of readable recipe text." }
        return text
    }
}
