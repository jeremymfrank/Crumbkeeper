package com.crumbkeeper.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/** Minimal local SQLite store for v0.1. Room migration planned after first device build. */
class RecipeStore(context: Context) : SQLiteOpenHelper(context, "crumbkeeper.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE recipes (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, data TEXT NOT NULL, updated_at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema upgrades must be additive; never delete users' recipes.
    }
    fun all(): List<Recipe> {
        val result = mutableListOf<Recipe>()
        readableDatabase.rawQuery("SELECT data FROM recipes ORDER BY updated_at DESC", null).use { cursor ->
            while (cursor.moveToNext()) runCatching { result += decodeRecipe(JSONObject(cursor.getString(0))) }
        }
        return result
    }
    fun save(recipe: Recipe) {
        val current = recipe.copy(updatedAt = System.currentTimeMillis())
        writableDatabase.execSQL("INSERT OR REPLACE INTO recipes(id,title,data,updated_at) VALUES(?,?,?,?)",
            arrayOf(current.id, current.title, encodeRecipe(current).toString(), current.updatedAt))
    }
    fun remove(id: String) {
        writableDatabase.delete("recipes", "id=?", arrayOf(id))
    }
}

fun encodeRecipe(r: Recipe): JSONObject = JSONObject().apply {
    put("id", r.id); put("title", r.title); put("servings", r.servings)
    put("notes", r.notes); put("coverImage", r.coverImage); put("originalText", r.originalText)
    put("updatedAt", r.updatedAt)
    put("ingredients", JSONArray().apply { r.ingredients.forEach { put(it) } })
    put("steps", JSONArray().apply { r.steps.forEach { step ->
        put(JSONObject().apply {
            put("id", step.id); put("instruction", step.instruction)
            put("timers", JSONArray().apply { step.timers.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id); put("label", t.label); put("durationSeconds", t.durationSeconds)
                    put("approved", t.approved)
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
        originalText = json.optString("originalText"), updatedAt = json.optLong("updatedAt"),
        ingredients = (0 until ingredients.length()).map { ingredients.optString(it) },
        steps = (0 until steps.length()).map { index ->
            val step = steps.optJSONObject(index) ?: JSONObject()
            val timers = step.optJSONArray("timers") ?: JSONArray()
            RecipeStep(id = step.optString("id", newId()), instruction = step.optString("instruction"),
                timers = (0 until timers.length()).map { tIndex ->
                    val t = timers.optJSONObject(tIndex) ?: JSONObject()
                    TimerPreset(id = t.optString("id", newId()), label = t.optString("label", "Timer"),
                        durationSeconds = t.optInt("durationSeconds", 0), approved = t.optBoolean("approved", true))
                })
        }
    )
}
