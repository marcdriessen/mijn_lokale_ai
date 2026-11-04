package com.example.auralocalai.data.coach

/** One answer of the language coach, split into its parts. */
data class CoachReply(
    /** Corrected version of the learner's sentence, or null when it was already right. */
    val correction: String?,
    /** Short explanation of the correction in the learner's own language. */
    val explanation: String?,
    /** The coach's reply in the language being practised. */
    val reply: String,
    /** The reply translated into the learner's own language. */
    val translation: String?
) {
    companion object {
        private val LABELS = mapOf(
            "CORRECTION" to "c", "CORRECTIE" to "c", "КОРЕКЦИЯ" to "c",
            "EXPLANATION" to "e", "UITLEG" to "e", "ОБЯСНЕНИЕ" to "e",
            "REPLY" to "r", "ANSWER" to "r", "ANTWOORD" to "r", "ОТГОВОР" to "r",
            "TRANSLATION" to "t", "VERTALING" to "t", "ПРЕВОД" to "t"
        )
        private val LABEL_RE = Regex("^\\s*[*#_ ]*(${LABELS.keys.joinToString("|")})[*_ ]*\\s*[:：]\\s*", RegexOption.IGNORE_CASE)

        /** Parses the model output; text without labels is treated as the reply. */
        fun parse(raw: String, learnerText: String): CoachReply {
            val parts = mutableMapOf<String, StringBuilder>()
            var current = "r"
            var sawLabel = false
            for (line in raw.lines()) {
                val m = LABEL_RE.find(line)
                if (m != null) {
                    current = LABELS.getValue(m.groupValues[1].uppercase())
                    sawLabel = true
                    parts.getOrPut(current) { StringBuilder() }.append(line.substring(m.range.last + 1).trim())
                } else if (line.isNotBlank()) {
                    val sb = parts.getOrPut(current) { StringBuilder() }
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(line.trim())
                }
            }
            fun get(k: String) = parts[k]?.toString()?.replace("**", "")?.trim()?.ifBlank { null }
            var correction = get("c")
            var explanation = get("e")
            if (correction != null && (isOk(correction) || sameIgnoringCaseAndPunctuation(correction, learnerText))) {
                correction = null
                explanation = null
            }
            val reply = get("r") ?: if (!sawLabel) raw.trim() else ""
            return CoachReply(correction, explanation, reply, get("t"))
        }

        private fun isOk(text: String): Boolean {
            val t = text.trim().trimEnd('.', '!').lowercase()
            return t in setOf("ok", "okay", "correct", "goed", "geen", "none", "-", "—", "n/a", "няма", "правилно", "geen fouten", "no mistakes")
        }

        /** Only capital letters or punctuation differ: not worth a correction for a chat. */
        fun sameIgnoringCaseAndPunctuation(a: String, b: String): Boolean {
            fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }.split(Regex("\\s+")).filter { it.isNotEmpty() }
            return norm(a) == norm(b)
        }
    }
}
