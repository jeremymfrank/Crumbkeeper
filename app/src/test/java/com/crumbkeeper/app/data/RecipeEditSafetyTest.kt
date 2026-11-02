package com.crumbkeeper.app.data

import org.junit.Assert.*
import org.junit.Test

class RecipeEditSafetyTest {
    private val saved = Recipe(title = "Smashed Potatoes",
        steps = listOf(RecipeStep(instruction = "Bake 20 minutes",
            timers = listOf(TimerPreset(label = "Oven", durationSeconds = 1200)))))

    @Test fun cookingAndSharingCannotModifyOriginal() {
        val copy = RecipeEditSafety.editableCopy(saved)
        assertNotEquals(saved.id, copy.id)
        assertNotEquals(saved.steps.single().id, copy.steps.single().id)
        assertNotEquals(saved.steps.single().timers.single().id, copy.steps.single().timers.single().id)
        assertEquals(saved.ingredients, copy.ingredients)
        assertEquals(saved.steps.single().timers.single().durationSeconds,
            copy.steps.single().timers.single().durationSeconds)
        assertEquals("Smashed Potatoes (Copy)", copy.title)
    }

    @Test fun savedRecipeRequiresExplicitFinalConfirmation() {
        assertThrows(IllegalArgumentException::class.java) {
            RecipeEditSafety.requireSafeSave(saved, true, setOf(saved.id), confirmedOriginal = false)
        }
        assertEquals(saved, RecipeEditSafety.requireSafeSave(saved, true, setOf(saved.id), confirmedOriginal = true))
    }

    @Test fun resumedDraftCannotSilentlyReplaceExistingRecipe() {
        assertThrows(IllegalArgumentException::class.java) {
            RecipeEditSafety.requireSafeSave(saved, false, setOf(saved.id), confirmedOriginal = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecipeEditSafety.requireSafeSave(saved, true, emptySet(), confirmedOriginal = true)
        }
    }

    @Test fun newRecipeCanBeSavedWithoutEditConfirmation() {
        assertEquals(saved, RecipeEditSafety.requireSafeSave(saved, false, emptySet(), confirmedOriginal = false))
    }
}
