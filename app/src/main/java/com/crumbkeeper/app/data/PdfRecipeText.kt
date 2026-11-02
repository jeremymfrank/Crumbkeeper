package com.crumbkeeper.app.data

/**
 * Conservative cleaning of OCR text from a website's printed recipe.
 * Never invent missing ingredients or measurements. Original OCR text is retained separately.
 */
object PdfRecipeText {
    data class Clean(val text: String, val warnings: List<String>)
    private val ingredients = Regex("(?i)^ingredients(?:\\s*[:\\-].*)?$")
    private val directions = Regex("(?i)^(?:instructions|directions|method|preparation|steps)(?:\\s*[:\\-].*)?$")
    private val stop = Regex("(?i)^(?:nutrition(?: facts| information)?|related recipes|you may also like|more recipes|leave a (?:reply|comment)|comments|reviews|rate (?:this|the) recipe|about the author|privacy policy|disclaimer)(?:\\b.*)?$")
    private val junk = Regex("(?i)^(?:advertisement|advertising|sponsored|skip to (?:recipe|content)|jump to recipe|print recipe|save recipe|share(?: on)? (?:facebook|pinterest)|cookie (?:policy|settings)|accept (?:all )?cookies|subscribe|sign in|log in|back to top|all rights reserved|\\d+ (?:comments?|reviews?))$")
    private val metadata = Regex("(?i)^(?:prep(?:aration)? time|cook(?:ing)? time|total time|calories|rating|author|yield|servings?|print|ingredients|instructions|directions|notes?|equipment|video)\\b.*$")
    private val number = Regex("^\\d+[.)]\\s*(.*)$")

    fun clean(ocr: String): Clean {
        val lines = ocr.lines().map { it.replace('\u00a0', ' ').trim().replace(Regex("\\s{2,}"), " ") }
            .filter { it.isNotBlank() }
            .filterNot { junk.matches(it) || it.startsWith("http://") || it.startsWith("https://") }
        val ing = lines.indexOfFirst { ingredients.matches(it) }
        val steps = lines.indexOfFirst { directions.matches(it) }
        if (ing < 0 || steps < 0 || steps <= ing) {
            return Clean(ocr.take(120_000), listOf(
                "Could not reliably separate PDF ingredients and directions. Review extracted text under Original Text."
            ))
        }
        val title = lines.take(ing).asReversed().firstOrNull { candidate ->
            candidate.length in 5..90 && candidate.any { it.isLetter() } &&
                !metadata.matches(candidate) &&
                !candidate.matches(Regex("(?i)^\\d+(?:\\.\\d+)?\\s*(?:min|minutes?|hours?|calories?)$")) &&
                !candidate.contains("www.", ignoreCase = true) &&
                candidate.count { it == ' ' } < 13
        }.orEmpty()
        val ingredientLines = lines.subList(ing + 1, steps)
            .takeWhile { !stop.matches(it) }
            .filter { !junk.matches(it) && !metadata.matches(it) }
        val instructionLines = lines.drop(steps + 1)
            .takeWhile { !stop.matches(it) }
            .filter { !junk.matches(it) }
        val stepLines = mutableListOf<String>()
        if (instructionLines.any { number.matches(it) }) {
            var current = ""
            instructionLines.forEach { line ->
                val match = number.matchEntire(line)
                if (match != null) {
                    if (current.isNotBlank()) stepLines += current
                    current = match.groupValues[1].trim()
                } else if (current.isNotBlank()) current = "$current $line"
            }
            if (current.isNotBlank()) stepLines += current
        } else {
            // Leave non-numbered lines separate for human review; never manufacture step boundaries.
            stepLines += instructionLines
        }
        val cleaned = buildString {
            if (title.isNotBlank()) appendLine("# $title")
            appendLine("## Ingredients")
            ingredientLines.forEach { appendLine("- $it") }
            appendLine("## Instructions")
            stepLines.forEachIndexed { index, step -> appendLine("${index + 1}. $step") }
        }
        val warnings = mutableListOf<String>()
        if (title.isBlank()) warnings += "PDF recipe title needs review."
        if (ingredientLines.isEmpty() || stepLines.isEmpty()) warnings += "Some recipe sections appear empty after extraction."
        warnings += "Obvious website decoration was excluded where identifiable. Compare against the original PDF."
        return Clean(cleaned, warnings)
    }
}
