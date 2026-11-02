package com.crumbkeeper.app.data

import org.junit.Assert.*
import org.junit.Test

class RecipeParserTest {
    @Test fun parsesOrdinaryChatGptRecipeAndProposesTimers() {
        val input = """# Smashed Golden Potatoes
            |Serves 4
            |## Ingredients
            |- 1.5 lb golden potatoes
            |- 2 tbsp olive oil
            |## Directions
            |1. Wash the potatoes.
            |2. Simmer 20 minutes.
            |3. Bake at 415°F for 20 minutes.
            |## Notes
            |Add baking soda to taste.
        """.trimMargin()
        val result = RecipeParser.parse(input)
        assertEquals("Smashed Golden Potatoes", result.recipe.title)
        assertEquals("4", result.recipe.servings)
        assertEquals(2, result.recipe.ingredients.size)
        assertEquals(3, result.recipe.steps.size)
        assertEquals(2, result.recipe.steps.sumOf { it.timers.size })
        assertTrue(result.recipe.steps.flatMap { it.timers }.all { !it.approved })
        assertTrue(result.recipe.originalText.contains("415°F"))
    }
    @Test fun standardizedChatGptMarkdownImportsExplicitTimer() {
        val text = """CRUMBKEEPER RECIPE v1
            |# Baked Bread
            |Serves: 8
            |## Ingredients
            |- 3 cups flour
            |## Instructions
            |1. Mix the flour and water.
            |2. Bake in a hot oven for 30 minutes.
            |   Timer: Bake | 1800s
            |## Notes
            |Cool before slicing.
        """.trimMargin()
        val parsed = RecipeParser.parse(text)
        assertEquals("Baked Bread", parsed.recipe.title)
        assertEquals(2, parsed.recipe.steps.size)
        val timers = parsed.recipe.steps.last().timers
        assertEquals(1, timers.size)
        assertTrue(timers.single().approved)
        assertEquals(1800, timers.single().durationSeconds)
        assertTrue(parsed.recipe.notes.contains("Cool before slicing"))
    }

    @Test fun rangeDoesNotSilentlyCreateTimer() {
        val parsed = RecipeParser.parse("Bread\nIngredients\n- 1 cup flour\nInstructions\nBake 20–25 minutes at 375°F")
        assertEquals(0, parsed.recipe.steps.single().timers.size)
        assertTrue(parsed.warnings.any { "range" in it.lowercase() })
    }
    @Test fun looseInputRetainsAllOriginalText() {
        val original = "Try adding a little salt and cook until ready."
        val result = RecipeParser.parse(original)
        assertEquals(original, result.recipe.originalText)
        assertTrue(result.warnings.isNotEmpty())
    }
}
