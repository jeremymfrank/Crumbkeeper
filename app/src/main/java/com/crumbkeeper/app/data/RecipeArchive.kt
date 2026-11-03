package com.crumbkeeper.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Offline versioned ZIP archives. Recipes, pictures and timer presets travel together. */
object RecipeArchive {
    const val MIME_TYPE = "application/vnd.crumbkeeper.recipe"
    const val BOOK_MIME_TYPE = "application/vnd.crumbkeeper.book"
    private const val VERSION = 1
    private const val MAX_RECIPES = 3000
    private const val MAX_ENTRIES = 6500
    private const val MAX_TOTAL = 128L * 1024 * 1024
    private const val MAX_IMAGE = 12L * 1024 * 1024
    private const val MAX_JSON = 2L * 1024 * 1024
    private val safeId = Regex("^[a-zA-Z0-9_-]{1,128}$")

    data class Prepared(val recipes: List<Recipe>, val isBook: Boolean, val images: Map<String, ByteArray>) {
        fun duplicateCount(existing: Set<String>): Int = recipes.count { it.id in existing }
    }
    data class Result(val imported: Int, val skipped: Int)

    fun writeRecipe(recipe: Recipe, output: OutputStream) = writeArchive(listOf(recipe), false, output)
    fun writeBook(recipes: List<Recipe>, output: OutputStream) = writeArchive(recipes, true, output)

    private fun writeArchive(recipes: List<Recipe>, book: Boolean, output: OutputStream) {
        require(recipes.size in 1..MAX_RECIPES) { "Invalid number of recipes to export." }
        val paths = recipes.map { r ->
            require(safeId.matches(r.id)) { "Invalid recipe ID." }
            if (book) "recipes/${r.id}.json" else "recipe.json"
        }
        val manifest = JSONObject().apply {
            put("format", if (book) "crumbkeeper.book" else "crumbkeeper.recipe")
            put("schemaVersion", VERSION)
            put("recipes", JSONArray(paths))
        }
        ZipOutputStream(output).use { zip ->
            fun add(path: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry()
            }
            add("manifest.json", manifest.toString(2).toByteArray(Charsets.UTF_8))
            recipes.forEachIndexed { index, r ->
                val picture = r.coverImage?.let(::File)?.also {
                    require(it.isFile) { "Missing recipe cover photo: ${r.title}" }
                }
                val mediaPath = picture?.let { "images/${r.id}.cover" }
                add(paths[index], encodeRecipe(r.copy(coverImage = mediaPath)).toString(2).toByteArray(Charsets.UTF_8))
                if (picture != null && mediaPath != null) {
                    require(picture.length() <= MAX_IMAGE) { "Cover photo is too large to export: ${r.title}" }
                    zip.putNextEntry(ZipEntry(mediaPath))
                    picture.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Validate fully before writing to the recipe database. Cap extracted bytes and entry names. */
    fun prepare(input: InputStream): Prepared {
        val parts = linkedMapOf<String, ByteArray>()
        var total = 0L
        var count = 0
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                count++
                require(count <= MAX_ENTRIES && !entry.isDirectory) { "Invalid archive entries." }
                val name = entry.name
                require(validName(name) && name !in parts) { "Unsafe or duplicate archive filename." }
                val max = if (name.startsWith("images/")) MAX_IMAGE else MAX_JSON
                val data = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var size = 0L
                while (true) {
                    val n = zip.read(buf)
                    if (n < 0) break
                    size += n; total += n
                    require(size <= max && total <= MAX_TOTAL) { "Recipe archive exceeds limits." }
                    data.write(buf, 0, n)
                }
                parts[name] = data.toByteArray()
                zip.closeEntry()
            }
        }
        val manifest = JSONObject(String(parts["manifest.json"] ?: error("Missing manifest."), Charsets.UTF_8))
        require(manifest.optInt("schemaVersion") == VERSION) { "Unsupported archive version." }
        val format = manifest.optString("format")
        require(format == "crumbkeeper.book" || format == "crumbkeeper.recipe") { "Not a Crumbkeeper archive." }
        val list = manifest.optJSONArray("recipes") ?: error("Missing recipe listing.")
        require(list.length() in 1..MAX_RECIPES) { "Invalid recipe count." }
        if (format == "crumbkeeper.recipe") require(list.length() == 1 && list.getString(0) == "recipe.json") {
            "Invalid single-recipe package."
        }
        val usedPaths = hashSetOf<String>()
        val usedIds = hashSetOf<String>()
        val recipes = (0 until list.length()).map { index ->
            val path = list.getString(index)
            require(usedPaths.add(path) && (path == "recipe.json" ||
                (format == "crumbkeeper.book" && path.matches(Regex("^recipes/[a-zA-Z0-9_-]{1,128}\\.json$"))))) {
                "Invalid recipe path."
            }
            val recipe = decodeRecipe(JSONObject(String(parts[path] ?: error("Missing recipe data."), Charsets.UTF_8)))
            require(recipe.title.isNotBlank() && safeId.matches(recipe.id) && usedIds.add(recipe.id)) {
                "Invalid or duplicate recipe ID."
            }
            if (recipe.coverImage != null) {
                require(recipe.coverImage == "images/${recipe.id}.cover" && parts.containsKey(recipe.coverImage)) {
                    "Recipe cover image is missing."
                }
            }
            recipe
        }
        val media = recipes.mapNotNull { it.coverImage }.toSet()
        require(parts.keys.all { it == "manifest.json" || it in usedPaths || it in media }) {
            "Unexpected files in recipe archive."
        }
        return Prepared(recipes, format == "crumbkeeper.book", parts.filterKeys { it in media })
    }

    private fun validName(name: String): Boolean = name.length <= 200 &&
        !name.startsWith("/") && !name.contains('\\') &&
        name.split('/').none { it.isEmpty() || it == "." || it == ".." } &&
        (name == "manifest.json" || name == "recipe.json" ||
            name.matches(Regex("^recipes/[a-zA-Z0-9_-]{1,128}\\.json$")) ||
            name.matches(Regex("^images/[a-zA-Z0-9_-]{1,128}\\.cover$")))

    /** Import missing IDs by default; 'copies' gives every import a new ID. Never overwrite silently. */
    fun restore(context: Context, store: RecipeStore, pending: Prepared, copies: Boolean = false): Result {
        val existingIds = store.existingIds()
        val selected = pending.recipes.filter { copies || it.id !in existingIds }
        val imageDir = File(context.filesDir, "recipe-images").apply { mkdirs() }
        val newImages = mutableListOf<File>()
        val restored = mutableListOf<Recipe>()
        try {
            selected.forEach { recipe ->
                val photo = recipe.coverImage?.let { path ->
                    val data = pending.images[path] ?: error("Missing photo data.")
                    File(imageDir, "${UUID.randomUUID()}.img").also {
                        it.writeBytes(data); newImages += it
                    }.absolutePath
                }
                val newSteps = if (copies) recipe.steps.map { step ->
                    step.copy(id = newId(), timers = step.timers.map { it.copy(id = newId()) })
                } else recipe.steps
                restored += recipe.copy(
                    id = if (copies) newId() else recipe.id, coverImage = photo, steps = newSteps
                )
            }
            store.saveBatch(restored)
        } catch (e: Exception) {
            newImages.forEach { it.delete() }
            throw e
        }
        return Result(restored.size, pending.recipes.size - restored.size)
    }
}
