package com.crumbkeeper.app.data

import org.junit.Assert.*
import org.junit.Test

class PdfRecipeImportTest {
    @Test fun detectsPdfBeforeText() {
        assertEquals(RecipeFileSniffer.Kind.PDF,
            RecipeFileSniffer.kind("%PDF-1.7\n1 0 obj".toByteArray()))
        assertEquals(RecipeFileSniffer.Kind.PDF,
            RecipeFileSniffer.kind(ByteArray(0), "application/pdf"))
    }

    @Test fun rejectsBinaryInsteadOfThousandsOfUnplacedLines() {
        assertThrows(IllegalArgumentException::class.java) {
            RecipeFileSniffer.kind(byteArrayOf(0x00, 0x11, 0x12, 0x13))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecipeFileSniffer.decodeText(byteArrayOf(0x25, 0x11, 0x00, 0x12))
        }
    }

    @Test fun normalMarkdownStillWorks() {
        val text = "# Bread\n## Ingredients\n- 1 cup flour\n## Instructions\n1. Mix."
        assertEquals(RecipeFileSniffer.Kind.TEXT, RecipeFileSniffer.kind(text.toByteArray()))
        assertEquals(text, RecipeFileSniffer.decodeText(text.toByteArray()))
    }

    @Test fun cleansPrintedWebsiteTextWithoutInventingIngredients() {
        val text = """Subscribe
            |Best Fish Taco Recipe
            |Prep Time 10 mins
            |Ingredients
            |1 lb white fish
            |2 tbsp lime juice
            |Instructions
            |1. Mix the lime
            |juice and spices.
            |2. Bake for 15 minutes.
            |Nutrition Facts
            |Advertisement
            |Leave a comment
        """.trimMargin()
        val cleaned = PdfRecipeText.clean(text)
        val parsed = RecipeParser.parse(cleaned.text)
        assertEquals("Best Fish Taco Recipe", parsed.recipe.title)
        assertEquals(2, parsed.recipe.ingredients.size)
        assertEquals(2, parsed.recipe.steps.size)
        assertEquals("Mix the lime juice and spices.", parsed.recipe.steps[0].instruction)
        assertEquals(1, parsed.recipe.steps[1].timers.size)
        assertFalse(parsed.recipe.steps[1].timers.single().approved)
        assertFalse(cleaned.text.contains("Advertisement"))
        assertFalse(cleaned.text.contains("Nutrition Facts"))
    }

    @Test fun missingHeadingsDoNotBecomeFakeIngredients() {
        val text = "Best Fish Taco Recipe\nAdvertisements\nPrint Recipe\nNo directions available."
        val cleaned = PdfRecipeText.clean(text)
        val parsed = RecipeParser.parse(cleaned.text)
        assertTrue(parsed.recipe.ingredients.isEmpty())
        assertTrue(parsed.recipe.steps.isEmpty())
        assertTrue(cleaned.warnings.isNotEmpty())
    }
}
