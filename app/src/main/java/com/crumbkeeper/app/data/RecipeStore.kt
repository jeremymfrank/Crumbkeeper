package com.crumbkeeper.app.data

import android.content.Context
import android.content.ContentValues
import java.io.File
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/** Minimal local SQLite store for v0.1. Room migration planned after first device build. */
class RecipeStore(context: Context) : SQLiteOpenHelper(context, "crumbkeeper.db", null, 2) {
    private val appContext = context.applicationContext
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE recipes (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, data TEXT NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER NOT NULL DEFAULT 0)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Additive migration only: previously saved recipes remain active.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE recipes ADD COLUMN deleted_at INTEGER NOT NULL DEFAULT 0")
        }
    }
    fun existingIds(): Set<String> {
        val ids = mutableSetOf<String>()
        readableDatabase.rawQuery("SELECT id FROM recipes", null).use { cursor ->
            while (cursor.moveToNext()) ids += cursor.getString(0)
        }
        return ids
    }

    fun all(): List<Recipe> = listRecipes(trashed = false)
    fun trashed(): List<Recipe> = listRecipes(trashed = true)

    private fun listRecipes(trashed: Boolean): List<Recipe> {
        val result = mutableListOf<Recipe>()
        val selection = if (trashed) "deleted_at <> 0" else "deleted_at = 0"
        readableDatabase.rawQuery(
            "SELECT data FROM recipes WHERE $selection ORDER BY updated_at DESC", null
        ).use { cursor ->
            while (cursor.moveToNext()) runCatching {
                result += decodeRecipe(JSONObject(cursor.getString(0)))
            }
        }
        return result
    }
    fun save(recipe: Recipe) {
        val current = recipe.copy(updatedAt = System.currentTimeMillis())
        writableDatabase.execSQL("INSERT OR REPLACE INTO recipes(id,title,data,updated_at) VALUES(?,?,?,?)",
            arrayOf(current.id, current.title, encodeRecipe(current).toString(), current.updatedAt))
    }
    /** One transaction: if importing any recipe fails, none of them are committed. */
    fun saveBatch(batch: List<Recipe>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            batch.forEach { recipe ->
                val current = recipe.copy(updatedAt = System.currentTimeMillis())
                db.execSQL("INSERT OR REPLACE INTO recipes(id,title,data,updated_at) VALUES(?,?,?,?)",
                    arrayOf(current.id, current.title, encodeRecipe(current).toString(), current.updatedAt))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
    /** Moves only an active recipe into recoverable trash. Does not delete images. */
    fun moveToTrash(id: String): Boolean {
        val values = ContentValues().apply { put("deleted_at", System.currentTimeMillis().coerceAtLeast(1)) }
        return writableDatabase.update(
            "recipes", values, "id=? AND deleted_at=0", arrayOf(id)
        ) == 1
    }

    /** Restores the same ID, original notes, photos and timer presets. */
    fun restoreFromTrash(id: String): Boolean {
        val values = ContentValues().apply { put("deleted_at", 0L) }
        return writableDatabase.update(
            "recipes", values, "id=? AND deleted_at<>0", arrayOf(id)
        ) == 1
    }

    /**
     * A separate, confirmed command, only for recipes ALREADY in Trash.
     * Photos shared by other recipes are retained; only orphaned local media is cleaned.
     */
    fun deletePermanently(id: String, confirmed: Boolean): Boolean {
        RecipeTrashPolicy.requireConfirmedPermanentDelete(confirmed)
        val old = trashed().firstOrNull { it.id == id } ?: return false
        val deleted = writableDatabase.delete(
            "recipes", "id=? AND deleted_at<>0", arrayOf(id)
        ) == 1
        if (deleted) {
            val photo = old.coverImage?.let(::File)
            if (photo != null && photo.isFile) {
                val imagesRoot = File(appContext.filesDir, "recipe-images").canonicalFile
                val safePhoto = photo.canonicalFile
                // A copied recipe may share the same cover path.
                val stillUsed = (all() + trashed()).any { it.coverImage == old.coverImage }
                if (!stillUsed && safePhoto.parentFile == imagesRoot) safePhoto.delete()
            }
        }
        return deleted
    }
}

fun encodeRecipe(r: Recipe): JSONObject = JSONObject().apply {
    put("id", r.id); put("title", r.title); put("servings", r.servings)
    put("notes", r.notes); put("coverImage", r.coverImage); put("originalText", r.originalText)
    put("coverFraming", r.coverFraming.normalized().let { framing -> JSONObject().apply {
        put("x", framing.x.toDouble()); put("y", framing.y.toDouble()); put("zoom", framing.zoom.toDouble())
    } })
    put("updatedAt", r.updatedAt)
    put("ingredients", JSONArray().apply { r.ingredients.forEach { put(it) } })
    put("steps", JSONArray().apply { r.steps.forEach { step ->
        put(JSONObject().apply {
            put("id", step.id); put("instruction", step.instruction)
            put("timers", JSONArray().apply { step.timers.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id); put("label", t.label); put("durationSeconds", t.durationSeconds)
                    // Compatibility with older Crumbkeeper readers. Added timers are always available.
                    put("approved", true)
                })
            } })
        })
    } })
}

fun decodeRecipe(json: JSONObject): Recipe {
    val ingredients = json.optJSONArray("ingredients") ?: JSONArray()
    val steps = json.optJSONArray("steps") ?: JSONArray()
    return Recipe(
        id = json.optString("id", newId()), title = json.optString("title"),
        servings = json.optString("servings"), notes = json.optString("notes"),
        coverImage = json.optString("coverImage").takeIf { it.isNotBlank() && it != "null" },
        coverFraming = json.optJSONObject("coverFraming")?.let { framing ->
            CoverFraming(framing.optDouble("x", .5).toFloat(), framing.optDouble("y", .5).toFloat(),
                framing.optDouble("zoom", 1.0).toFloat()).normalized()
        } ?: CoverFraming(),
        originalText = json.optString("originalText"), updatedAt = json.optLong("updatedAt"),
        ingredients = (0 until ingredients.length()).map { ingredients.optString(it) },
        steps = (0 until steps.length()).map { index ->
            val step = steps.optJSONObject(index) ?: JSONObject()
            val timers = step.optJSONArray("timers") ?: JSONArray()
            RecipeStep(id = step.optString("id", newId()), instruction = step.optString("instruction"),
                timers = (0 until timers.length()).map { tIndex ->
                    val t = timers.optJSONObject(tIndex) ?: JSONObject()
                    TimerPreset(id = t.optString("id", newId()), label = t.optString("label", "Timer"),
                        durationSeconds = t.optInt("durationSeconds", 0))
                })
        }
    )
}
