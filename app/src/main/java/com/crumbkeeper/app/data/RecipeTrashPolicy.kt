package com.crumbkeeper.app.data

object RecipeTrashPolicy {
    fun requireConfirmedPermanentDelete(confirmed: Boolean) {
        require(confirmed) { "Permanent deletion requires a confirmation." }
    }
}
