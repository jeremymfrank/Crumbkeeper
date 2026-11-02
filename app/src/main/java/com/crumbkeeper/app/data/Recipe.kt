package com.crumbkeeper.app.data

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

data class TimerPreset(
    val id: String = newId(),
    val label: String = "Timer",
    val durationSeconds: Int = 300,
    val approved: Boolean = true
)

data class RecipeStep(
    val id: String = newId(),
    val instruction: String = "",
    val timers: List<TimerPreset> = emptyList()
)

data class Recipe(
    val id: String = newId(),
    val title: String = "",
    val servings: String = "",
    val ingredients: List<String> = emptyList(),
    val steps: List<RecipeStep> = emptyList(),
    val notes: String = "",
    val coverImage: String? = null,
    val originalText: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

data class ParsedRecipe(val recipe: Recipe, val warnings: List<String>, val unplaced: List<String>)
