package com.example.auralocalai.data.briefing

import kotlinx.serialization.Serializable

/** A topic the user follows. [keywords] are matched against headlines (any language). */
@Serializable
data class BriefingTopic(
    val id: String,
    val name: String,
    val keywords: List<String> = emptyList(),
    val enabled: Boolean = true
) {
    /** Name plus keywords, without duplicates or blanks. */
    val allKeywords: List<String>
        get() = (listOf(name) + keywords)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
}

/**
 * A news source. [url] is an RSS/Atom feed. When it contains "{q}", it is a search feed:
 * the app fills in each topic keyword (URL-encoded) and fetches it once per keyword.
 */
@Serializable
data class NewsSource(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true
) {
    val isSearchFeed: Boolean get() = url.contains(SEARCH_PLACEHOLDER)

    companion object {
        const val SEARCH_PLACEHOLDER = "{q}"
    }
}

@Serializable
data class NewsItem(
    val title: String,
    val link: String,
    val sourceName: String,
    val publishedMillis: Long? = null,
    val description: String = ""
)

@Serializable
data class BriefingSection(
    val topicId: String,
    val topicName: String,
    val summary: String = "",
    val items: List<NewsItem> = emptyList()
)

@Serializable
data class Briefing(
    val id: String,
    val createdAt: Long,
    val sections: List<BriefingSection>,
    val language: String = "en"
)

@Serializable
data class BriefingConfig(
    val topics: List<BriefingTopic> = emptyList(),
    val sources: List<NewsSource> = emptyList(),
    val autoUpdateEnabled: Boolean = false,
    val autoUpdateHour: Int = 8,
    val maxItemsPerTopic: Int = 6
)

/** News fetched in the background (or earlier), reused when it is still recent. */
@Serializable
data class FetchCache(
    val fetchedAt: Long = 0L,
    val items: List<NewsItem> = emptyList()
)

object DefaultBriefing {

    private fun googleNewsSite(site: String): String =
        "https://news.google.com/rss/search?q=site:$site+when:1d&hl=en-US&gl=US&ceid=US:en"

    val sources: List<NewsSource> = listOf(
        NewsSource("dw", "DW", "https://rss.dw.com/rdf/rss-en-all"),
        NewsSource("aljazeera", "Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml"),
        NewsSource("reuters", "Reuters (via Google News)", googleNewsSite("reuters.com")),
        NewsSource("apnews", "AP News (via Google News)", googleNewsSite("apnews.com")),
        NewsSource(
            "gnews_search_en",
            "Google News search (English)",
            "https://news.google.com/rss/search?q={q}+when:1d&hl=en-US&gl=US&ceid=US:en",
            enabled = false
        ),
        NewsSource(
            "gnews_search_bg",
            "Google Новини търсене (български)",
            "https://news.google.com/rss/search?q={q}+when:1d&hl=bg&gl=BG&ceid=BG:bg",
            enabled = false
        ),
        NewsSource(
            "gnews_bg_top",
            "Google Новини (България)",
            "https://news.google.com/rss?hl=bg&gl=BG&ceid=BG:bg",
            enabled = false
        )
    )

    val topics: List<BriefingTopic> = listOf(
        BriefingTopic("tech_ai", "Tech & AI", listOf("AI", "artificial intelligence", "kunstmatige intelligentie", "изкуствен интелект", "technology", "tech", "OpenAI", "Google", "Apple", "chip")),
        BriefingTopic("netherlands", "Nederland", listOf("Netherlands", "Dutch", "Nederland", "Nederlandse", "Amsterdam", "Нидерландия")),
        BriefingTopic("bulgaria", "България", listOf("Bulgaria", "Bulgarian", "Bulgarije", "Sofia", "България", "София")),
        BriefingTopic("health_science", "Gezondheid & Wetenschap", listOf("health", "science", "medical", "study", "research", "gezondheid", "wetenschap", "здраве", "наука")),
        BriefingTopic("economy", "Economie & Beurs", listOf("economy", "markets", "stocks", "inflation", "economie", "beurs", "икономика", "борса")),
        BriefingTopic("world_politics", "Wereldpolitiek", listOf("election", "president", "minister", "war", "Ukraine", "Gaza", "EU", "NATO", "verkiezingen", "избори"), enabled = false)
    )

    val config: BriefingConfig = BriefingConfig(topics = topics, sources = sources)
}
