package com.crumbkeeper.app.data

/** Readable, portable ChatGPT-friendly text format, imported through RecipeParser. */
object RecipeTextFormat {
    const val HEADER = "CRUMBKEEPER RECIPE v1"
    fun toMarkdown(recipe: Recipe): String = buildString {
        appendLine(HEADER)
        appendLine("# ${recipe.title}")
        if (recipe.servings.isNotBlank()) appendLine("Serves: ${recipe.servings}")
        appendLine(); appendLine("## Ingredients")
        recipe.ingredients.filter { it.isNotBlank() }.forEach { appendLine("- $it") }
        appendLine(); appendLine("## Instructions")
        recipe.steps.forEachIndexed { i, step ->
            appendLine("${i + 1}. ${step.instruction}")
            step.timers.filter { it.durationSeconds > 0 }.forEach {
                appendLine("   Timer: ${it.label} | ${it.durationSeconds}s")
            }
        }
        if (recipe.notes.isNotBlank()) {
            appendLine(); appendLine("## Notes"); appendLine(recipe.notes)
        }
    }
}
