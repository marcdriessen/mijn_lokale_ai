package com.example.auralocalai.data.briefing

import kotlinx.serialization.Serializable

/** A study behind a news item (found via its DOI). */
@Serializable
data class LinkedStudy(
    val title: String,
    val journal: String = "",
    val year: String = "",
    val url: String = "",
    val type: String = ""
)

/**
 * Pure helpers for smarter news handling (unit-tested):
 * relevance scores from the local model, grouping of reports about the same event, DOI detection
 * and prompts to compare sources.
 */
object NewsIntelligence {

    // ---------------- Relevance (tier 2, local model) ----------------

    /** One prompt for many headlines, so ranking costs a single short model call. */
    fun relevancePrompt(instructions: String, topicNames: List<String>, items: List<NewsItem>): String =
        buildString {
            append("Reader's interests: ").append(instructions.ifBlank { topicNames.joinToString(", ") }).append('\n')
            append("Topics: ").append(topicNames.joinToString(", ")).append("\n\n")
            append("Rate how relevant each news item is for this reader, from 0 (not relevant) to 10 (very relevant).\n")
            append("Reply with one line per item in the form <number>: <score>, nothing else.\n\n")
            items.forEachIndexed { i, item ->
                append(i + 1).append(". ").append(item.title.take(160))
                if (item.description.isNotBlank()) append(" — ").append(item.description.take(140))
                append('\n')
            }
        }

    const val RELEVANCE_SYSTEM = "You rank news for one reader. You only output numbers in the requested format."

    /** Parses "3: 8" / "3. 8/10" / "3 - 8" lines into item index (0-based) → score 0..10. */
    fun parseScores(raw: String?, count: Int): Map<Int, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        val line = Regex("^\\s*(\\d{1,3})\\s*[:.)\\-–]\\s*(?:score\\s*[:=]?\\s*)?(\\d{1,2})(?:\\s*/\\s*10)?", RegexOption.IGNORE_CASE)
        return raw.lines().mapNotNull { l ->
            val m = line.find(l) ?: return@mapNotNull null
            val index = m.groupValues[1].toInt() - 1
            val score = m.groupValues[2].toInt()
            if (index in 0 until count && score in 0..10) index to score else null
        }.toMap()
    }

    /**
     * Keeps the order of [candidates] but moves items the model found more relevant forward.
     * Items without a score keep a neutral 5, so a failed model call changes nothing.
     */
    fun <T> rankByScores(candidates: List<T>, scores: Map<Int, Int>): List<T> =
        candidates.withIndex()
            .sortedWith(compareByDescending<IndexedValue<T>> { scores[it.index] ?: 5 }.thenBy { it.index })
            .map { it.value }

    // ---------------- Same event, several sources ----------------

    private val STOP = setOf(
        "de", "het", "een", "en", "van", "in", "op", "te", "voor", "met", "is", "na", "bij", "om", "over", "aan", "door", "uit",
        "the", "a", "an", "and", "of", "in", "on", "to", "for", "with", "is", "after", "at", "by", "from", "as", "says", "new",
        "и", "на", "в", "за", "с", "от", "се", "да", "по"
    )

    /** Meaningful words of a headline (lower case, 3+ letters, without filler words or publisher suffix). */
    fun words(title: String): Set<String> =
        title.substringBeforeLast(" - ").lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 && it !in STOP }
            .toSet()

    fun similarity(a: String, b: String): Double {
        val wa = words(a)
        val wb = words(b)
        if (wa.isEmpty() || wb.isEmpty()) return 0.0
        return (wa intersect wb).size.toDouble() / (wa union wb).size
    }

    /**
     * For each chosen item: other reports (from other sources) about the same event. Reprints of
     * the same text are not independent confirmation, so items from the same source are skipped.
     */
    fun relatedReports(chosen: NewsItem, pool: List<NewsItem>, threshold: Double = 0.34, max: Int = 4): List<NewsItem> =
        pool.asSequence()
            .filter { it.link != chosen.link && it.sourceName != chosen.sourceName }
            .map { it to similarity(chosen.title, it.title) }
            .filter { it.second >= threshold }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.sourceName }
            .take(max)
            .toList()

    /** Status labels per claim (as in the integration plan). */
    val CLAIM_LABELS = listOf(
        "[SUPPORTED]" to "reported consistently by two or more independent sources",
        "[PARTLY SUPPORTED]" to "confirmed in part, details differ",
        "[CONFLICTING]" to "sources contradict each other",
        "[SINGLE SOURCE]" to "reported by only one source, not verified",
        "[INSUFFICIENT]" to "too little information in the texts to judge",
        "[REFUTED]" to "contradicted by the other sources"
    )

    /** Prompt to compare several reports of one event, with a status label per claim. */
    fun comparePrompt(languageName: String, reports: List<NewsItem>): String = buildString {
        append("Compare these news reports about the same event. Write in ").append(languageName).append(".\n")
        append("List the main claims. Give each claim exactly one status label and the sources [numbers] that report it:\n")
        CLAIM_LABELS.forEach { (label, meaning) -> append("- ").append(label).append(": ").append(meaning).append('\n') }
        append("Then add a short section 'Extra details per source'.\n")
        append("Use only the texts below. Do not add facts. Reprints of the same text are not independent confirmation.\n\n")
        reports.forEachIndexed { i, r ->
            append("[").append(i + 1).append("] ").append(r.sourceName).append(": ").append(r.title).append('\n')
            if (r.description.isNotBlank()) append(r.description.take(600)).append('\n')
            append(r.link).append("\n\n")
        }
    }

    // ---------------- Studies behind news ----------------

    private val DOI = Regex("\\b(10\\.\\d{4,9}/[^\\s\"'<>()\\[\\]]+)", RegexOption.IGNORE_CASE)

    /** First DOI mentioned in an article text or link (trailing punctuation removed). */
    fun findDoi(vararg texts: String): String? =
        texts.asSequence().mapNotNull { DOI.find(it)?.groupValues?.get(1) }
            .map { it.trimEnd('.', ',', ';', ':') }
            .firstOrNull()

    /** Keywords that suggest an article is about research. */
    private val RESEARCH = listOf("study", "studie", "onderzoek", "research", "trial", "review", "journal", "published", "изследване", "проучване")

    fun mentionsResearch(text: String): Boolean = RESEARCH.any { text.lowercase().contains(it) }
}
