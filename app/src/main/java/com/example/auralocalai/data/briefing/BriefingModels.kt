package com.example.auralocalai.data.briefing

import kotlinx.serialization.Serializable

/** Kind of source. Forum items are shown as discussions/experiences, never as verified knowledge. */
object SourceCategory {
    const val NEWS = "news"
    const val PROFESSIONAL = "professional"
    const val SCIENCE = "science"
    const val TECH = "tech"
    const val FORUM = "forum"
    val ALL = listOf(NEWS, PROFESSIONAL, SCIENCE, TECH, FORUM)
}

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
    val enabled: Boolean = true,
    val category: String = SourceCategory.NEWS
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
    val imageUrl: String? = null,
    /** Domain of the real publisher (Google News items link to news.google.com). */
    val publisherDomain: String = "",
    /** Full text from the feed itself (content:encoded), when the feed provides it. */
    val content: String = "",
    /** Category of the source: news, professional, science, tech or forum (forum = experiences, not verified). */
    val sourceCategory: String = SourceCategory.NEWS
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
    val processed: Boolean = false,
    /** What the summary is based on: "full" (whole article), "feed" (feed text) or "summary_only". */
    val textStatus: String = "",
    /** Reports about the same event from other sources (for comparison). */
    val related: List<NewsItem> = emptyList(),
    /** The study behind a research news item, found via its DOI. */
    val study: LinkedStudy? = null
)

@Serializable
data class Briefing(
    val id: String,
    val createdAt: Long,
    val sections: List<BriefingSection> = emptyList(),
    val language: String = "en",
    val profileId: String = "",
    val profileName: String = "",
    val articles: List<BriefingArticle> = emptyList(),
    /** All other matching items that were found but not chosen for the briefing ("All results"). */
    val moreItems: List<NewsItem> = emptyList()
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
    val maxItemsPerTopic: Int = 6,
    /** Skip sites where most articles are behind a paywall. */
    val skipPaywalled: Boolean = true,
    /** Version of the built-in source list, used to add new free sources to older configurations. */
    val sourcesVersion: Int = 0
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

    /** Free news sites with their own feed (no Google News detour, no paywall). Added in sources version 2. */
    val freeSources: List<NewsSource> = listOf(
        NewsSource("bbc_world", "BBC World", "https://feeds.bbci.co.uk/news/world/rss.xml"),
        NewsSource("guardian_world", "The Guardian (World)", "https://www.theguardian.com/world/rss"),
        NewsSource("france24", "France 24", "https://www.france24.com/en/rss"),
        NewsSource("nu_algemeen", "NU.nl", "https://www.nu.nl/rss/Algemeen"),
        NewsSource("nu_gezondheid", "NU.nl Gezondheid", "https://www.nu.nl/rss/Gezondheid"),
        NewsSource("nos_binnenland", "NOS Binnenland", "https://feeds.nos.nl/nosnieuwsbinnenland"),
        NewsSource("dnevnik", "Дневник", "https://www.dnevnik.bg/rss/"),
        NewsSource("novinite", "Novinite (Bulgaria, English)", "https://www.novinite.com/services/news_rdf.php")
    )

    /**
     * Added in sources version 3 (advice of the integration plan): Bulgarian news (BTA, Fakti),
     * the Dutch health inspectorate (IGJ) and Google News fallbacks for professional sites.
     * Technology and forum feeds are added switched off.
     */
    val sourcesV3: List<NewsSource> = listOf(
        NewsSource("bta_bg", "БТА – България", "https://www.bta.bg/bg/news/bulgaria/rss"),
        NewsSource("fakti", "Факти", "https://fakti.bg/feed"),
        NewsSource("fakti_bg", "Факти – България", "https://fakti.bg/feed/bulgaria"),
        NewsSource("igj", "Inspectie Gezondheidszorg en Jeugd", "https://www.igj.nl/service/rss", category = SourceCategory.PROFESSIONAL),
        NewsSource("igj_toezicht", "IGJ toezichtdocumenten", "https://toezichtdocumenten.igj.nl/api/rss", category = SourceCategory.PROFESSIONAL),
        NewsSource("gn_nispa", "NISPA (via Google Nieuws)", "https://news.google.com/rss/search?q=site%3Anispa.nl+when%3A30d&hl=nl&gl=NL&ceid=NL:nl", category = SourceCategory.PROFESSIONAL),
        NewsSource("gn_ggzstandaarden", "GGZ Standaarden (via Google Nieuws)", "https://news.google.com/rss/search?q=site%3Aggzstandaarden.nl+when%3A30d&hl=nl&gl=NL&ceid=NL:nl", category = SourceCategory.PROFESSIONAL),
        NewsSource("hackernews", "Hacker News", "https://news.ycombinator.com/rss", enabled = false, category = SourceCategory.TECH),
        NewsSource("xda", "XDA Developers", "https://www.xda-developers.com/feed/", enabled = false, category = SourceCategory.TECH)
    )

    /** Added in sources version 4: forums (experiences, not verified knowledge), switched off. */
    val sourcesV4: List<NewsSource> = listOf(
        NewsSource("busfreaks", "Busfreaks (forum)", "https://www.busfreaks.de/forum/thread-list-rss-feed/", enabled = false, category = SourceCategory.FORUM)
    )

    const val SOURCES_VERSION = 4

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
        sources = sources + freeSources + dutchSources + sourcesV3 + sourcesV4,
        profiles = listOf(careProfile, worldProfile),
        sourcesVersion = SOURCES_VERSION
    )

    /** Adds profiles and the Dutch sources to a configuration saved by an older version. */
    fun migrate(config: BriefingConfig): BriefingConfig {
        var result = config
        if (result.profiles.isEmpty()) {
            val existingIds = result.sources.map { it.id }.toSet()
            val world = worldProfile.copy(topics = result.topics.ifEmpty { topics })
            result = result.copy(
                sources = result.sources + dutchSources.filterNot { it.id in existingIds },
                profiles = listOf(careProfile, world)
            )
        }
        if (result.sourcesVersion < SOURCES_VERSION) {
            val existingIds = result.sources.map { it.id }.toSet()
            val added = (if (result.sourcesVersion < 2) freeSources else emptyList()) +
                (if (result.sourcesVersion < 3) sourcesV3 else emptyList()) + sourcesV4
            result = result.copy(
                sources = result.sources + added.filterNot { it.id in existingIds },
                sourcesVersion = SOURCES_VERSION
            )
        }
        return result
    }
}
