package com.crumbkeeper.app.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class TimerAvailabilityTest {
    @Test fun recoveredSessionAddsPreviouslyHiddenTimersWithoutResettingRunningTimers() {
        val recipe = Recipe(id = "bread", title = "Bread", steps = listOf(RecipeStep(timers = listOf(
            TimerPreset("rise", "Rise", 2700), TimerPreset("bake", "Bake", 1800)))))
        val running = TimerRun("rise", 2_700_000, "running", deadlineMs = 50_000)
        val restored = SessionSnapshot("bread", checkedSteps = setOf("step-1"), timers = mapOf("rise" to running))
            .withRecipeTimers(recipe)
        assertEquals(running, restored.timers["rise"])
        assertEquals("ready", restored.timers["bake"]!!.state)
        assertEquals(1_800_000, restored.timers["bake"]!!.durationMs)
        assertEquals(setOf("step-1"), restored.checkedSteps)
    }

    @Test fun oldDisabledFlagNoLongerHidesAnAddedTimer() {
        val recipe = decodeRecipe(JSONObject("""{"id":"old-bread","title":"Bread","steps":[
            {"id":"rise-step","instruction":"Let rise.","timers":[
              {"id":"rise","label":"Rise","durationSeconds":2700,"approved":false}]}]}"""))
        val timer = recipe.steps.single().timers.single()
        assertEquals("rise", timer.id)
        assertEquals(2700, timer.durationSeconds)
        assertTrue(encodeRecipe(recipe).getJSONArray("steps").getJSONObject(0)
            .getJSONArray("timers").getJSONObject(0).getBoolean("approved"))
        assertEquals("ready", TimerRun(timer.id, timer.durationSeconds.toLong() * 1000).state)
    }

    @Test fun backupAndRecipeSharingPreserveEveryIndependentTimer() {
        val original = Recipe(id = "bread", title = "Bread", steps = listOf(RecipeStep(id = "step", instruction = "Rise and bake.",
            timers = listOf(TimerPreset("rise", "Rise", 2700), TimerPreset("bake", "Bake", 1800)))))
        for (book in listOf(false, true)) {
            val output = ByteArrayOutputStream()
            if (book) RecipeArchive.writeBook(listOf(original), output) else RecipeArchive.writeRecipe(original, output)
            val result = RecipeArchive.prepare(ByteArrayInputStream(output.toByteArray())).recipes.single()
            assertEquals(original.steps, result.steps)
            assertEquals(listOf("ready", "ready"), result.steps.single().timers.map {
                TimerRun(it.id, it.durationSeconds.toLong() * 1000).state
            })
        }
    }

    @Test fun explicitTimersReplaceInferenceAndKeepMultipleIndependentPresets() {
        val result = RecipeParser.parse("""Bread
            Ingredients
            1 cup flour
            Instructions
            1. Let rise for 45 minutes, then bake for 30 minutes.
            Timer: Rise | 2700s
            Timer: Bake | 1800s
        """.trimIndent()).recipe
        assertEquals(listOf("Rise", "Bake"), result.steps.single().timers.map { it.label })
        assertEquals(listOf(2700, 1800), result.steps.single().timers.map { it.durationSeconds })
    }
}
