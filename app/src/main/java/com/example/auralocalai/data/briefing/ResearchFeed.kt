package com.example.auralocalai.data.briefing

import com.example.auralocalai.data.medical.EuropePmcClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * New scientific studies per topic, as extra items for the briefing (Europe PMC, which includes
 * PubMed). Uses the English keywords of each topic and the last [days] days.
 */
object ResearchFeed {

    /** English-looking keywords (Latin letters only), most specific first, max 4. */
    fun englishKeywords(topic: BriefingTopic): List<String> =
        topic.allKeywords
            .filter { k -> k.length >= 4 && k.all { it.isLetter() && it.code < 128 || it == ' ' || it == '-' } }
            .sortedByDescending { it.length }
            .take(4)

    fun query(keywords: List<String>, from: String, to: String): String =
        "(" + keywords.joinToString(" OR ") { "\"$it\"" } + ") AND FIRST_PDATE:[$from TO $to]"

    suspend fun fetch(topics: List<BriefingTopic>, days: Int = 30, perTopic: Int = 4): List<NewsItem> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        val to = fmt.format(Date())
        val from = fmt.format(Date(System.currentTimeMillis() - days * 24L * 3600L * 1000L))
        val client = EuropePmcClient()
        return topics.filter { it.enabled }.flatMap { topic ->
            val keywords = englishKeywords(topic)
            if (keywords.isEmpty()) return@flatMap emptyList()
            runCatching { client.search(query(keywords, from, to), maxResults = perTopic) }.getOrDefault(emptyList()).map { a ->
                NewsItem(
                    title = a.title,
                    link = a.url,
                    sourceName = listOf("Europe PMC", a.journal).filter { it.isNotBlank() }.joinToString(" · "),
                    publishedMillis = runCatching { fmt.parse(a.publishedDate)?.time }.getOrNull(),
                    description = a.abstract.take(500),
                    content = a.abstract,
                    sourceCategory = SourceCategory.SCIENCE
                )
            }
        }.distinctBy { it.link }
    }
}
