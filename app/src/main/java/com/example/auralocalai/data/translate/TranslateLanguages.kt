package com.example.auralocalai.data.translate

/** Target languages offered in the Translate mini-app (all supported offline by ML Kit). */
object TranslateLanguages {

    data class Language(val code: String, val nativeName: String, val englishName: String)

    val ALL = listOf(
        Language("nl", "Nederlands", "Dutch"),
        Language("en", "English", "English"),
        Language("bg", "Български", "Bulgarian"),
        Language("pl", "Polski", "Polish"),
        Language("de", "Deutsch", "German"),
        Language("fr", "Français", "French"),
        Language("es", "Español", "Spanish"),
        Language("it", "Italiano", "Italian"),
        Language("pt", "Português", "Portuguese"),
        Language("ro", "Română", "Romanian"),
        Language("tr", "Türkçe", "Turkish"),
        Language("ar", "العربية", "Arabic"),
        Language("uk", "Українська", "Ukrainian"),
        Language("ru", "Русский", "Russian")
    )

    fun englishName(code: String): String? = ALL.firstOrNull { it.code == code }?.englishName

    fun nativeName(code: String): String = ALL.firstOrNull { it.code == code }?.nativeName ?: code

    /**
     * Splits a long text into parts of at most [maxChars], cutting only between paragraphs
     * (or between lines when a single paragraph is too long), so the model sees whole sentences.
     */
    fun chunks(text: String, maxChars: Int = 1_500): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() { if (current.isNotBlank()) out += current.toString().trim(); current.clear() }
        for (paragraph in text.split(Regex("\n\\s*\n"))) {
            val pieces = if (paragraph.length <= maxChars) listOf(paragraph) else paragraph.split('\n')
            for (piece in pieces) {
                if (current.length + piece.length + 2 > maxChars) flush()
                if (piece.length > maxChars) {
                    piece.chunked(maxChars).forEach { out += it.trim() }
                } else {
                    if (current.isNotEmpty()) current.append(if (pieces.size == 1) "\n\n" else "\n")
                    current.append(piece)
                }
            }
            if (pieces.size > 1) flush()
        }
        flush()
        return out.filter { it.isNotBlank() }
    }
}
