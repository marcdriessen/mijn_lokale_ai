package com.example.auralocalai.data.medical

/**
 * Prompts for the clinical Q&A. The model may only use the numbered sources, must cite them,
 * must say so when the sources do not answer the question, and must never state doses.
 */
object MedicalPrompts {

    fun systemPrompt(languageName: String): String =
        "You are a careful clinical information assistant for nurses and physicians in mental health care, " +
            "addiction care and general (somatic) care. Answer in $languageName. " +
            "Rules: (1) Use ONLY the numbered sources given by the user; do not add facts from memory. " +
            "(2) Cite sources with their number in square brackets, like [1] or [2]. " +
            "(3) If the sources do not answer the question, say so clearly instead of guessing. " +
            "(4) Never state medication doses, dosing schedules, titration steps or maximum doses; " +
            "for doses, refer to the Farmacotherapeutisch Kompas. " +
            "(5) Do not make a diagnosis or treatment decision for an individual patient. " +
            "(6) Be concise and structured. " +
            "(7) Weigh the evidence: a guideline or systematic review counts more than a single study, a study more than " +
            "a news report, and a forum or personal experience is not evidence. For a study, mention when the source text " +
            "states it: design (e.g. RCT, cohort, review), population, number of participants, main result and limitations. " +
            "(8) Sources marked 'Kennisbank' are machine-translated question-and-answer summaries of research articles or " +
            "textbooks; treat them as background knowledge, not as a Dutch guideline, and mention when Dutch practice may differ."

    /** User prompt with numbered sources, trimmed to [charBudget] so it fits the context window. */
    fun answerPrompt(question: String, sources: List<MedicalSource>, languageName: String, charBudget: Int): String {
        val header = "Question: $question\n\nSources:\n"
        val footer = "\nAnswer the question in $languageName using only these sources. " +
            "Start with a direct answer in 1-2 sentences, then up to 5 bullet points (\"• \") with the key points, " +
            "each with its source number. For studies, add in brackets the design and number of participants when the " +
            "source states them, e.g. (RCT, n=240). End with one line on what the sources do not cover, if relevant."
        val builder = StringBuilder(header)
        val available = charBudget - header.length - footer.length
        val perSource = if (sources.isEmpty()) 0 else (available / sources.size).coerceIn(250, 1_200)
        sources.forEachIndexed { index, source ->
            val entry = "[${index + 1}] ${source.title} (${source.origin})\n${source.snippet.take(perSource)}\n\n"
            if (builder.length - header.length + entry.length > available && index > 0) return@forEachIndexed
            builder.append(entry)
        }
        builder.append(footer)
        return builder.toString()
    }

    const val PUBMED_QUERY_SYSTEM_PROMPT = "You convert clinical questions into short PubMed search queries. Reply with the query only."

    fun pubMedQueryPrompt(question: String): String =
        "Convert this clinical question into a short PubMed search query in English " +
            "(3 to 6 key terms, you may use AND/OR, no explanation).\nQuestion: $question"

    /** Removes quotes, labels and extra lines the model may add around a query. */
    fun cleanQuery(raw: String): String {
        val line = raw.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return ""
        return line.removePrefix("Query:").removePrefix("query:").trim().trim('"', '\'', '`').take(200)
    }

    /** Brave query restricted to the enabled trusted sites. */
    fun siteQuery(question: String, domains: List<String>): String {
        val sites = domains.joinToString(" OR ") { "site:$it" }
        return if (domains.isEmpty()) question.take(300) else "${question.take(250)} ($sites)"
    }
}
