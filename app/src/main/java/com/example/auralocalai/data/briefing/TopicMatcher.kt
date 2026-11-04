package com.example.auralocalai.data.briefing

import java.util.Locale

/**
 * Decides which news items belong to which topic by matching keywords against the headline
 * and description. Pure Kotlin (no Android) so it is unit-testable.
 */
object TopicMatcher {

    /** Keywords of 3 characters or fewer (AI, EU, F1) must match as a whole word. */
    private const val SHORT_KEYWORD_LENGTH = 3

    fun matches(item: NewsItem, keywords: List<String>): Boolean {
        val haystack = normalize("${item.title} ${item.description}")
        return keywords.any { keyword -> containsKeyword(haystack, normalize(keyword)) }
    }

    fun containsKeyword(normalizedText: String, normalizedKeyword: String): Boolean {
        if (normalizedKeyword.isBlank()) return false
        if (normalizedKeyword.length > SHORT_KEYWORD_LENGTH) {
            return normalizedText.contains(normalizedKeyword)
        }
        var start = normalizedText.indexOf(normalizedKeyword)
        while (start >= 0) {
            val end = start + normalizedKeyword.length
            val beforeOk = start == 0 || !normalizedText[start - 1].isLetterOrDigit()
            val afterOk = end >= normalizedText.length || !normalizedText[end].isLetterOrDigit()
            if (beforeOk && afterOk) return true
            start = normalizedText.indexOf(normalizedKeyword, start + 1)
        }
        return false
    }

    fun normalize(text: String): String =
        text.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()

    /** Key used to drop duplicate stories (same headline from several feeds). */
    fun dedupeKey(item: NewsItem): String =
        normalize(item.title).replace(Regex("[^\\p{L}\\p{N} ]"), "").take(80)

    /**
     * Items for one topic: matching, without duplicates, newest first, at most [max].
     */
    fun selectForTopic(items: List<NewsItem>, topic: BriefingTopic, max: Int): List<NewsItem> {
        val keywords = topic.allKeywords
        return items
            .filter { matches(it, keywords) }
            .distinctBy { dedupeKey(it) }
            .sortedByDescending { it.publishedMillis ?: 0L }
            .take(max)
    }

    /** Splits a comma/semicolon/newline separated keyword list typed by the user or the model. */
    fun parseKeywordList(text: String, max: Int = 15): List<String> =
        text.split(',', ';', '\n', '•')
            .map { it.trim().trim('-', '*', '"', '\'', '.', ' ') }
            .filter { it.isNotEmpty() && it.length <= 60 }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(max)
}
