package com.crumbkeeper.app.data

/**
 * Enforces the two-phase saved-recipe editing workflow.
 *
 * All library and cooking actions remain read-only. Existing recipes may only
 * be overwritten from a deliberate edit session after explicit save confirmation.
 */
object RecipeEditSafety {
    fun editableCopy(original: Recipe): Recipe = original.copy(
        id = newId(),
        title = if (original.title.endsWith(" (Copy)")) original.title else original.title + " (Copy)",
        steps = original.steps.map { step ->
            step.copy(id = newId(), timers = step.timers.map { it.copy(id = newId()) })
        }
    )

    fun requireSafeSave(draft: Recipe, editingExisting: Boolean,
                        savedIds: Set<String>, confirmedOriginal: Boolean): Recipe {
        require(draft.title.isNotBlank()) { "Give this recipe a title first." }
        if (editingExisting) {
            require(confirmedOriginal) { "Confirm replacing the saved recipe first." }
            require(draft.id in savedIds) { "The original recipe no longer exists." }
        } else {
            require(draft.id !in savedIds) { "This recipe already exists. Save a separate copy." }
        }
        return draft
    }
}
