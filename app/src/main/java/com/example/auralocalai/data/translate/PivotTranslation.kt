package com.example.auralocalai.data.translate

/**
 * "Via English" mode for small models: the question is translated to English, the model answers in
 * English (where small models are much stronger) and the answer is translated back.
 * This helper translates markdown text line by line and leaves code blocks, list markers and
 * headings intact, so the structure of the answer survives the translation.
 */
object PivotTranslation {

    private val PREFIX = Regex("^(\\s*(?:#{1,6}\\s+|[-*•]\\s+|\\d+[.)]\\s+|>\\s*)?)(.*)$")

    suspend fun translateMarkdown(text: String, translateLine: suspend (String) -> String?): String {
        val out = StringBuilder()
        var inCode = false
        for ((i, line) in text.lines().withIndex()) {
            if (i > 0) out.append('\n')
            if (line.trimStart().startsWith("```")) {
                inCode = !inCode
                out.append(line)
                continue
            }
            if (inCode || line.isBlank() || !line.any { it.isLetter() }) {
                out.append(line)
                continue
            }
            val m = PREFIX.find(line)
            val prefix = m?.groupValues?.get(1).orEmpty()
            val body = m?.groupValues?.get(2) ?: line
            // Keep bold markers around the whole line (e.g. "**Tip:** ...") readable after translation.
            val translated = translateLine(body.replace("**", "")) ?: body
            out.append(prefix).append(translated)
        }
        return out.toString()
    }

    /** Instruction added to the user's turn so the model answers in English. */
    const val ANSWER_IN_ENGLISH = "(Answer in English.)"
}
