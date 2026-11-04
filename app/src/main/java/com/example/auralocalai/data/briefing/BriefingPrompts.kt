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
}
