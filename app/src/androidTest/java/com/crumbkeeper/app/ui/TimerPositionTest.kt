package com.crumbkeeper.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.crumbkeeper.app.data.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TimerPositionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun timerStaysInItsStepAcrossStartPauseResumeFinishDismissAndCancel() {
        val preset = TimerPreset(id = "rise", label = "Rise", durationSeconds = 2700)
        val recipe = Recipe(id = "bread", title = "Bread", steps = (1..12).map { index ->
            RecipeStep(id = "step-$index", instruction = "Recipe instruction $index.",
                timers = if (index == 5) listOf(preset) else emptyList())
        })
        var runtime by mutableStateOf(TimerRun(preset.id, 2_700_000))
        compose.setContent {
            MaterialTheme {
                CookingScreen(recipe, SessionSnapshot(recipe.id, timers = mapOf(runtime.id to runtime)), 1000,
                    onIngredient = {}, onStep = {}, onTimer = { _, action ->
                        runtime = when (action) {
                            "start", "resume" -> runtime.copy(state = "running", deadlineMs = 2_701_000)
                            "pause" -> runtime.copy(state = "paused", remainingMs = 2_700_000)
                            else -> TimerRun(preset.id, 2_700_000)
                        }
                    }, finish = {})
            }
        }
        compose.onNodeWithTag("timer-rise").performScrollTo()
        val initialTimer = bounds("timer-rise")
        val initialStep = bounds("step-step-5")
        fun unchanged() {
            compose.waitForIdle()
            assertBounds(initialTimer, bounds("timer-rise"))
            assertBounds(initialStep, bounds("step-step-5"))
            compose.onNodeWithText("ACTIVE TIMERS").assertDoesNotExist()
        }
        fun click(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("timer-rise"))).performClick()
        click("Start"); unchanged()
        click("Pause"); unchanged()
        click("Resume"); unchanged()
        compose.runOnIdle { runtime = runtime.copy(state = "done", remainingMs = 0) }
        unchanged()
        click("Dismiss"); unchanged()
        click("Start"); unchanged()
        click("Cancel")
        compose.onNodeWithText("Cancel Timer").performClick()
        unchanged()
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun assertBounds(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 1f)
        assertEquals(expected.top, actual.top, 1f)
        assertEquals(expected.right, actual.right, 1f)
        assertEquals(expected.bottom, actual.bottom, 1f)
    }
}
