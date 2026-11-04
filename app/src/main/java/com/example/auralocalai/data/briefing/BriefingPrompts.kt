package com.example.auralocalai.data.briefing

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Prompts for the local model. Kept small: on-device models have 2K-8K token windows. */
object BriefingPrompts {

    fun systemPrompt(languageName: String): String =
        "You are an editorial news assistant. You write short, factual news briefings in $languageName. " +
            "Use only the facts given by the user. Never invent facts, numbers or quotes."

    /**
     * Builds the user prompt for one topic. Articles are added until [charBudget] is reached,
     * so the prompt always fits the model's context window.
     */
    fun topicPrompt(topicName: String, items: List<NewsItem>, languageName: String, charBudget: Int): String {
        val header = "Topic: $topicName\n\nNews articles:\n"
        val footer = "\nWrite a briefing about this topic in $languageName: one short intro sentence, " +
            "then 3 to 5 bullet points (starting with \"• \") with the key facts. " +
            "Mention the source name in brackets after each bullet, for example (Reuters). " +
            "If the articles are about different events, cover the most important ones. No title, no closing remarks."
        val builder = StringBuilder(header)
        val dateFormat = SimpleDateFormat("d MMM HH:mm", Locale.US)
        val available = charBudget - header.length - footer.length
        items.forEachIndexed { index, item ->
            val date = item.publishedMillis?.let { " [" + dateFormat.format(Date(it)) + "]" } ?: ""
            val description = item.description.take(280)
            val entry = "${index + 1}. ${item.title} (${item.sourceName})$date\n" +
                (if (description.isNotBlank()) "   $description\n" else "")
            if (builder.length - header.length + entry.length > available && index > 0) return@forEachIndexed
            builder.append(entry)
        }
        builder.append(footer)
        return builder.toString()
    }

    fun keywordPrompt(topicName: String): String =
        "Topic: \"$topicName\"\n" +
            "Give news search keywords for this topic in Dutch, English and Bulgarian (Bulgarian in Cyrillic). " +
            "Include common synonyms, abbreviations and important names. " +
            "Reply with ONLY one line of comma-separated keywords, at most 12, no explanations."

    const val KEYWORD_SYSTEM_PROMPT = "You translate and expand news search keywords. You reply with one comma-separated line only."

    /** Character budget for the article list, proportional to the model's context window (tokens). */
    fun charBudgetForContext(contextTokens: Int): Int =
        (contextTokens * 2).coerceIn(1_500, 9_000)

    /** System prompt for one article: the user's own instructions steer focus and tone. */
    fun articleSystemPrompt(languageName: String, instructions: String): String =
        "You are an editorial news assistant writing for one reader. You ALWAYS write in $languageName, " +
            "also when the article is in English or another language: then you translate it into $languageName. " +
            "Use only the facts in the article text given; never invent facts, numbers or quotes. " +
            (if (instructions.isNotBlank()) "The reader's instructions: ${instructions.trim()}" else "")

    /**
     * Prompt for one article. The answer must have three labelled lines so it can be parsed:
     * TITLE, SUMMARY and RELEVANCE.
     */
    fun articlePrompt(item: NewsItem, topicName: String, languageName: String): String =
        "Topic: $topicName\n" +
            "Headline: ${item.title}\n" +
            "Source: ${item.sourceName}\n" +
            (if (item.description.isNotBlank()) "Article text: ${item.description.take(900)}\n" else "") +
            "\nThe article may be in another language. Translate and write everything in $languageName.\n" +
            "Reply with exactly these three lines and nothing else:\n" +
            "TITLE: <the headline translated into $languageName, max 12 words>\n" +
            "SUMMARY: <2 short sentences with the key facts>\n" +
            "RELEVANCE: <1 or 2 sentences on what this means for the reader, based on the reader's instructions; " +
            "say honestly if it has no direct meaning>\n" +
            "Remember: TITLE, SUMMARY and RELEVANCE must be written in $languageName."

    data class ArticleText(val title: String, val summary: String, val relevance: String)

    /** Parses the three labelled lines; tolerant of missing labels, bullets and translated labels. */
    fun parseArticle(raw: String): ArticleText {
        var title = ""
        var summary = ""
        var relevance = ""
        val loose = mutableListOf<String>()
        raw.lines().map { it.trim().trimStart('*', '-', '•', ' ') }.filter { it.isNotEmpty() }.forEach { line ->
            val label = line.substringBefore(':', "").trim().trim('*').trim().lowercase()
            val value = line.substringAfter(':', "").trim().trim('*').trim()
            when {
                label in TITLE_LABELS -> title = value
                label in SUMMARY_LABELS -> summary = value
                label in RELEVANCE_LABELS -> relevance = value
                else -> loose += line
            }
        }
        if (summary.isEmpty() && loose.isNotEmpty()) summary = loose.joinToString(" ")
        return ArticleText(title, summary, relevance)
    }

    private val TITLE_LABELS = setOf("title", "titel", "заглавие", "headline", "kop")
    private val SUMMARY_LABELS = setOf("summary", "samenvatting", "резюме", "обобщение")
    private val RELEVANCE_LABELS = setOf("relevance", "betekenis", "betekenis voor jouw werk", "relevantie", "значение", "значимост")
}
