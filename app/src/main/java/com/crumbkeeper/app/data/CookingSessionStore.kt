package com.crumbkeeper.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Session storage is deliberately separate from recipe presets. Timers have wall-clock deadlines. */
data class TimerRun(
    val id: String,
    val durationMs: Long,
    val state: String = "ready",
    val deadlineMs: Long = 0L,
    val remainingMs: Long = durationMs
) {
    fun remaining(now: Long): Long = when (state) {
        "running" -> (deadlineMs - now).coerceAtLeast(0L)
        "paused" -> remainingMs
        "done" -> 0L
        else -> durationMs
    }
    fun normalized(now: Long): TimerRun =
        if (state == "running" && deadlineMs <= now) copy(state = "done", remainingMs = 0L) else this
}

data class SessionSnapshot(
    val recipeId: String,
    val checkedIngredients: Set<Int> = emptySet(),
    val checkedSteps: Set<String> = emptySet(),
    val timers: Map<String, TimerRun> = emptyMap()
) {
    /** Recover newly available legacy presets without changing existing running deadlines. */
    fun withRecipeTimers(recipe: Recipe): SessionSnapshot {
        if (recipe.id != recipeId) return this
        val missing = recipe.steps.flatMap { it.timers }.filter { it.durationSeconds > 0 && it.id !in timers }
            .associate { it.id to TimerRun(it.id, it.durationSeconds.toLong() * 1000) }
        return copy(timers = timers + missing)
    }
}

class CookingSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("cook-session", Context.MODE_PRIVATE)
    fun save(s: SessionSnapshot) {
        val data = JSONObject().apply {
            put("recipeId", s.recipeId)
            put("checkedIngredients", JSONArray(s.checkedIngredients.toList()))
            put("checkedSteps", JSONArray(s.checkedSteps.toList()))
            put("timers", JSONArray().apply { s.timers.values.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id); put("durationMs", t.durationMs); put("state", t.state)
                    put("deadlineMs", t.deadlineMs); put("remainingMs", t.remainingMs)
                })
            } })
        }
        prefs.edit().putString("snapshot", data.toString()).apply()
    }
    fun load(): SessionSnapshot? = runCatching {
        val text = prefs.getString("snapshot", null) ?: return@runCatching null
        val o = JSONObject(text)
        val ingr = o.optJSONArray("checkedIngredients") ?: JSONArray()
        val steps = o.optJSONArray("checkedSteps") ?: JSONArray()
        val timers = o.optJSONArray("timers") ?: JSONArray()
        SessionSnapshot(
            recipeId = o.getString("recipeId"),
            checkedIngredients = (0 until ingr.length()).map { ingr.getInt(it) }.toSet(),
            checkedSteps = (0 until steps.length()).map { steps.getString(it) }.toSet(),
            timers = (0 until timers.length()).map { i ->
                val t = timers.getJSONObject(i)
                TimerRun(id = t.getString("id"), durationMs = t.getLong("durationMs"),
                    state = t.getString("state"), deadlineMs = t.getLong("deadlineMs"),
                    remainingMs = t.getLong("remainingMs"))
            }.associateBy { it.id }
        )
    }.getOrNull()
    fun clear() { prefs.edit().remove("snapshot").apply() }
}
