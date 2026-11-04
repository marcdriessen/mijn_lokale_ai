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
    val description: String = "",
    val imageUrl: String? = null
)

@Serializable
data class BriefingSection(
    val topicId: String,
    val topicName: String,
    val summary: String = "",
    val items: List<NewsItem> = emptyList()
)

/** One article in an editorial briefing, rewritten by the model for the reader. */
@Serializable
data class BriefingArticle(
    val item: NewsItem,
    val topicName: String,
    val title: String = "",
    val summary: String = "",
    val relevance: String = "",
    /** True when the model rewrote (and translated) the article. */
    val processed: Boolean = false
)

@Serializable
data class Briefing(
    val id: String,
    val createdAt: Long,
    val sections: List<BriefingSection> = emptyList(),
    val language: String = "en",
    val profileId: String = "",
    val profileName: String = "",
    val articles: List<BriefingArticle> = emptyList()
)

enum class BriefingFrequency { DAILY, WEEKDAYS, WEEKLY }

enum class SourceMode { RSS, WEB, BOTH }

/**
 * A briefing the user configured: what it is about (instructions + topics), where the news comes
 * from (RSS feeds and/or web search on chosen domains) and when it runs.
 */
@Serializable
data class BriefingProfile(
    val id: String,
    val name: String,
    val instructions: String = "",
    val topics: List<BriefingTopic> = emptyList(),
    val sourceMode: String = SourceMode.RSS.name,
    /** Domains for web search (Brave, site-restricted), e.g. nos.nl, ggznieuws.nl. */
    val webDomains: List<String> = emptyList(),
    val maxArticles: Int = 6,
    val scheduleEnabled: Boolean = false,
    val frequency: String = BriefingFrequency.DAILY.name,
    /** Calendar day of week (1 = Sunday … 7 = Saturday) for WEEKLY. */
    val weekday: Int = 2,
    val hour: Int = 8,
    val minute: Int = 0,
    val startDateMillis: Long = 0L,
    val notify: Boolean = true,
    /** Language the model writes the briefing in ("nl", "en", "bg"), or "" for the app language. */
    val language: String = ""
) {
    val mode: SourceMode get() = runCatching { SourceMode.valueOf(sourceMode) }.getOrDefault(SourceMode.RSS)
    val schedule: BriefingFrequency get() = runCatching { BriefingFrequency.valueOf(frequency) }.getOrDefault(BriefingFrequency.DAILY)
}

@Serializable
data class BriefingConfig(
    val topics: List<BriefingTopic> = emptyList(),
    val sources: List<NewsSource> = emptyList(),
    val profiles: List<BriefingProfile> = emptyList(),
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

    private fun googleNewsNl(site: String): String =
        "https://news.google.com/rss/search?q=site:$site+when:2d&hl=nl&gl=NL&ceid=NL:nl"

    /** Dutch sources for care news (via Google News, which offers reliable feeds per site). */
    val dutchSources: List<NewsSource> = listOf(
        NewsSource("nos", "NOS", "https://feeds.nos.nl/nosnieuwsalgemeen"),
        NewsSource("ggznieuws", "GGZ Nieuws (via Google News)", googleNewsNl("ggznieuws.nl")),
        NewsSource("skipr", "Skipr (via Google News)", googleNewsNl("skipr.nl")),
        NewsSource("zorgvisie", "Zorgvisie (via Google News)", googleNewsNl("zorgvisie.nl")),
        NewsSource("trimbos_news", "Trimbos (via Google News)", googleNewsNl("trimbos.nl")),
        NewsSource(
            "gnews_search_nl",
            "Google Nieuws zoeken (Nederlands)",
            "https://news.google.com/rss/search?q={q}+when:2d&hl=nl&gl=NL&ceid=NL:nl"
        )
    )

    const val CARE_PROFILE_ID = "profile_care"

    val careProfile: BriefingProfile = BriefingProfile(
        id = CARE_PROFILE_ID,
        name = "Dagelijks nieuws over ggz en verslavingszorg",
        instructions = "Maak iedere dag een beknopt Nederlandstalig nieuwsoverzicht over ggz en verslavingszorg, " +
            "toegespitst op mijn werk als psychiatrisch verpleegkundige in de Nederlandse verslavingszorg. " +
            "Geef voorrang aan Nederlandse ontwikkelingen: beleid, onderzoek, behandeling, arbeidsvoorwaarden en scholing. " +
            "Leg per bericht kort uit wat het betekent voor mijn werk.",
        topics = listOf(
            BriefingTopic("care_ggz", "Ggz", listOf("ggz", "geestelijke gezondheidszorg", "psychiatrie", "psychiatrisch", "mental health", "psychiatry")),
            BriefingTopic("care_addiction", "Verslavingszorg", listOf("verslaving", "verslavingszorg", "alcohol", "drugs", "middelengebruik", "addiction", "Tactus", "Novadic-Kentron", "Jellinek", "Mondriaan", "Trimbos")),
            BriefingTopic("care_nursing", "Verpleegkundige praktijk", listOf("verpleegkundige", "verpleegkundigen", "V&VN", "cao ggz", "zorgpersoneel", "nurse", "nursing"))
        ),
        sourceMode = SourceMode.RSS.name,
        webDomains = listOf("ggznieuws.nl", "skipr.nl", "zorgvisie.nl", "nos.nl", "trimbos.nl", "nu.nl"),
        maxArticles = 6,
        language = "nl"
    )

    val worldProfile: BriefingProfile = BriefingProfile(
        id = "profile_world",
        name = "Wereldnieuws",
        instructions = "Geef een kort, feitelijk overzicht van het belangrijkste internationale nieuws.",
        topics = topics,
        sourceMode = SourceMode.RSS.name,
        maxArticles = 6
    )

    val config: BriefingConfig = BriefingConfig(
        topics = topics,
        sources = sources + dutchSources,
        profiles = listOf(careProfile, worldProfile)
    )

    /** Adds profiles and the Dutch sources to a configuration saved by an older version. */
    fun migrate(config: BriefingConfig): BriefingConfig {
        if (config.profiles.isNotEmpty()) return config
        val existingIds = config.sources.map { it.id }.toSet()
        val world = worldProfile.copy(topics = config.topics.ifEmpty { topics })
        return config.copy(
            sources = config.sources + dutchSources.filterNot { it.id in existingIds },
            profiles = listOf(careProfile, world)
        )
    }
}
