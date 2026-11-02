package com.crumbkeeper.app.data

/** Deliberately conservative, network-free extractor for pasted recipes (including ChatGPT Markdown).
 * Source text remains on the imported draft so no unmatched lines disappear.
 */
object RecipeParser {
    private val explicitTimer = Regex("(?i)^Timer:\\s*(.+?)\\s*\\|\\s*(\\d+)\\s*(s|sec|seconds|min|minutes|h|hours)$")
    private val servingsRegex = Regex("(?i)^(?:makes|yields|serves|servings)\\s*:?\\s*(.+)$")
    private val ingredientSection = Regex("(?i)^(ingredients|for the .+|dough|filling|topping|glaze)\\s*:?$")
    private val stepsSection = Regex("(?i)^(directions|method|instructions|preparation|steps)\\s*:?$")
    private val notesSection = Regex("(?i)^(notes?|tips?)\\s*:?$")
    private val numbered = Regex("^\\d+[.)]\\s*(.+)$")
    private val timed = Regex("(?i)\\b(\\d+)\\s*(?:minutes?|mins?|hours?|hrs?)\\b")
    private val ranged = Regex("(?i)\\b(\\d+)\\s*(?:-|–|to)\\s*(\\d+)\\s*(minutes?|mins?|hours?|hrs?)\\b")
    private val ingredientsStart = Regex("(?i)^(?:[-*•]\\s*)?(?:\\d+(?:[ /]\\d+)?|[¼½¾⅓⅔⅛⅜⅝⅞]|a pinch|pinch|salt|pepper|to taste)\\b.*")
    private val metaHead = Regex("(?i)^(?:prep(?:aration)? time|cook(?:ing)? time|total time|difficulty|oven temperature)\\s*:")

    fun parse(raw: String): ParsedRecipe {
        val source = raw.trim()
        if (source.isEmpty()) return ParsedRecipe(Recipe(), listOf("Nothing was pasted."), emptyList())
        val lines = source.lines().map { line ->
            line.trim().replace(Regex("^#{1,6}\\s+"), "")
                .replace(Regex("^[-*•]\\s+"), "- ")
                .replace("**", "").replace("__", "")
                .trim().trimEnd(':').trim()
        }.filter { it.isNotBlank() && it !in listOf("---", "***", "```", "```markdown", "```text") }
        val ingredients = mutableListOf<String>()
        val steps = mutableListOf<RecipeStep>()
        val unplaced = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var title = ""
        var servings = ""
        var notes = ""
        var section = "intro"
        lines.forEach { line ->
            val clean = line.removePrefix("- ").trim()
            if (clean.equals("CRUMBKEEPER RECIPE v1", ignoreCase = true)) return@forEach
            if (clean.startsWith("Timer:", ignoreCase = true) && section == "steps") {
                val match = explicitTimer.matchEntire(clean)
                if (match == null || steps.isEmpty()) {
                    warnings += "Timer line needs review: ${clean}"
                    unplaced += clean
                } else {
                    val amount = match.groupValues[2].toIntOrNull() ?: 0
                    val unit = match.groupValues[3].lowercase()
                    val seconds = when (unit) {
                        "h", "hours" -> amount.toLong() * 3600L
                        "min", "minutes" -> amount.toLong() * 60L
                        else -> amount.toLong()
                    }
                    if (seconds <= 0 || seconds > 86400L) {
                        warnings += "Timer duration needs review: ${clean}"
                        unplaced += clean
                    } else {
                        val previous = steps.last()
                        // Explicit, human-authored timer lines replace unapproved inferred suggestions.
                        val retained = previous.timers.filter { it.approved }
                        steps[steps.lastIndex] = previous.copy(timers = retained +
                            TimerPreset(label = match.groupValues[1].trim(),
                                durationSeconds = seconds.toInt(), approved = true))
                    }
                }
                return@forEach
            }
            if (title.isBlank() && section == "intro" && clean.startsWith("Title:", ignoreCase = true)) {
                title = clean.substringAfter(':').trim()
                return@forEach
            }
            when {
                ingredientSection.matches(clean) -> { section = "ingredients"; return@forEach }
                stepsSection.matches(clean) -> { section = "steps"; return@forEach }
                notesSection.matches(clean) -> { section = "notes"; return@forEach }
            }
            if (clean.startsWith("Note:", ignoreCase = true) || clean.startsWith("Notes:", ignoreCase = true)) {
                val inlineNote = clean.substringAfter(':').trim()
                notes += if (notes.isEmpty()) inlineNote else "\n$inlineNote"
                section = "notes"
                return@forEach
            }
            val yieldMatch = servingsRegex.matchEntire(clean)
            if (yieldMatch != null) { servings = yieldMatch.groupValues[1]; return@forEach }
            if (title.isBlank() && section == "intro" && !metaHead.containsMatchIn(clean)) {
                title = clean
                return@forEach
            }
            when (section) {
                "ingredients" -> ingredients += clean
                "steps" -> steps += extractStep(numbered.matchEntire(clean)?.groupValues?.get(1) ?: clean, warnings)
                "notes" -> notes += if (notes.isEmpty()) clean else "\n$clean"
                else -> unplaced += clean
            }
        }
        if (steps.isEmpty() && ingredients.isEmpty()) {
            // Preserve unstructured pastes in editable text instead of claiming they parsed correctly.
            warnings += "Could not identify ingredient or instruction sections. Check Original Text."
        }
        if (title.isBlank()) warnings += "Recipe title needs review."
        if (unplaced.isNotEmpty()) warnings += "${unplaced.size} unplaced source line(s) preserved."
        val recipe = Recipe(title = title, servings = servings, ingredients = ingredients,
            steps = steps, notes = notes, originalText = source)
        return ParsedRecipe(recipe, warnings.distinct(), unplaced)
    }

    private fun extractStep(instruction: String, warnings: MutableList<String>): RecipeStep {
        if (ranged.containsMatchIn(instruction)) {
            warnings += "Time range needs review: ${instruction.take(70)}"
            return RecipeStep(instruction = instruction)
        }
        val proposed = timed.findAll(instruction).take(3).map { match ->
            val amount = match.groupValues[1].toIntOrNull() ?: 0
            val value = if (match.value.contains("hour", true) || match.value.contains("hr", true)) amount * 3600 else amount * 60
            val label = when {
                instruction.contains("rise", true) -> "Rise"
                instruction.contains("simmer", true) -> "Simmer"
                instruction.contains("bake", true) -> "Bake"
                instruction.contains("boil", true) -> "Boil"
                instruction.contains("rest", true) -> "Rest"
                else -> "Step Timer"
            }
            TimerPreset(label = label, durationSeconds = value, approved = false)
        }.filter { it.durationSeconds > 0 }.toList()
        return RecipeStep(instruction = instruction, timers = proposed)
    }
}
