package com.crumbkeeper.app.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Guards for the destructive operation; database migration, Trash UI and image
 * lifecycle need real Android device/instrumentation validation.
 */
class RecipeTrashPolicyTest {
    @Test fun deletingForeverNeedsExplicitConfirmation() {
        assertThrows(IllegalArgumentException::class.java) {
            RecipeTrashPolicy.requireConfirmedPermanentDelete(false)
        }
        RecipeTrashPolicy.requireConfirmedPermanentDelete(true)
    }
}
