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
    val category: String = SourceCategory.NEWS,
    /** Short description in Dutch (what the source is), shown when choosing sources. */
    val description: String = "",
    /** Language of the source ("nl", "en", "bg"), "" = unknown. */
    val language: String = ""
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
    val study: LinkedStudy? = null,
    /** Source that was replaced because it was behind a paywall or blocked (the news is shown from a free source). */
    val replacedSource: String = "",
    /** The whole article, prepared in advance and translated into the briefing language (paragraph by paragraph). */
    val fullText: List<FullParagraph> = emptyList()
)

/** One paragraph of a prepared article: the translation and the original text. */
@Serializable
data class FullParagraph(val text: String, val original: String = "")

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
    val language: String = "",
    /** Also look for new scientific studies (Europe PMC, includes PubMed) on the topics. */
    val includeResearch: Boolean = false
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

/**
 * Result of the last fetch of one source. A feed counts as working only when it answered AND
 * gave usable items (an empty or broken feed is reported).
 */
@Serializable
data class SourceStatus(
    val lastChecked: Long = 0L,
    val lastSuccess: Long = 0L,
    val itemCount: Int = 0,
    val error: String = ""
) {
    companion object {
        fun update(previous: SourceStatus?, now: Long, items: Int, error: String?): SourceStatus {
            val ok = error == null && items > 0
            return SourceStatus(
                lastChecked = now,
                lastSuccess = if (ok) now else previous?.lastSuccess ?: 0L,
                itemCount = items,
                error = error ?: if (items == 0) "no items" else ""
            )
        }
    }
}

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

    private fun gnBg(site: String) = "https://news.google.com/rss/search?q=site%3A$site+when%3A1d&hl=bg&gl=BG&ceid=BG:bg"

    /**
     * Added in sources version 5: the remaining sources of the integration plan. Sites without a
     * confirmed own feed (BNR, BNT, NOVA, Mediapool, 24 Chasa, Darik, V&VN) go via Google News.
     */
    val sourcesV5: List<NewsSource> = listOf(
        NewsSource("bta_free", "БТА – свободни новини", "https://www.bta.bg/bg/rss/free"),
        NewsSource("gn_bnr", "БНР (via Google News)", gnBg("bnr.bg")),
        NewsSource("gn_bnt", "БНТ (via Google News)", gnBg("bntnews.bg")),
        NewsSource("gn_nova", "NOVA (via Google News)", gnBg("nova.bg")),
        NewsSource("gn_mediapool", "Медиапул (via Google News)", gnBg("mediapool.bg")),
        NewsSource("gn_24chasa", "24 часа (via Google News)", gnBg("24chasa.bg")),
        NewsSource("gn_darik", "Дарик (via Google News)", gnBg("dariknews.bg")),
        NewsSource("gn_venvn", "V&VN (via Google Nieuws)", "https://news.google.com/rss/search?q=site%3Avenvn.nl+when%3A30d&hl=nl&gl=NL&ceid=NL:nl", category = SourceCategory.PROFESSIONAL),
        NewsSource("gn_nl_top", "Google Nieuws Nederland (algemeen)", "https://news.google.com/rss?hl=nl&gl=NL&ceid=NL:nl", enabled = false),
        NewsSource("xda_news", "XDA News", "https://www.xda-developers.com/feed/news/", enabled = false, category = SourceCategory.TECH)
    )

    /**
     * Sources version 6 (checked live on 4 Nov 2026): direct feeds instead of Google News where the
     * site has one, extra Dutch care and Bulgarian health/news sources, and a description per source.
     */
    val updatesV6: Map<String, Pair<String, String>> = mapOf(
        "ggznieuws" to ("GGZ Nieuws" to "https://www.ggznieuws.nl/feed/"),
        "skipr" to ("Skipr" to "https://www.skipr.nl/feed/"),
        "zorgvisie" to ("Zorgvisie" to "https://www.zorgvisie.nl/feed/"),
        "trimbos_news" to ("Trimbos-instituut" to "https://www.trimbos.nl/feed/"),
        "gn_nova" to ("NOVA" to "https://nova.bg/rss/latest"),
        "gn_mediapool" to ("Медиапул" to "https://www.mediapool.bg/feed/"),
        "gn_24chasa" to ("24 часа" to "https://www.24chasa.bg/rss"),
        "gn_darik" to ("Дарик" to "https://dariknews.bg/rss.php"),
        "gn_bnt" to ("БНТ" to "https://bntnews.bg/bg/rss/news"),
        "igj" to ("Inspectie Gezondheidszorg en Jeugd (via Google Nieuws)" to "https://news.google.com/rss/search?q=site%3Aigj.nl+when%3A30d&hl=nl&gl=NL&ceid=NL:nl")
    )

    val sourcesV6: List<NewsSource> = listOf(
        NewsSource("nursing_nl", "Nursing", "https://www.nursing.nl/feed/", category = SourceCategory.PROFESSIONAL),
        NewsSource("zorgwelzijn", "ZorgWelzijn", "https://www.zorgwelzijn.nl/feed/", category = SourceCategory.PROFESSIONAL),
        NewsSource("clinica_bg", "Clinica.bg", "https://clinica.bg/rss/news.xml", category = SourceCategory.PROFESSIONAL),
        NewsSource("zdrave_net", "Zdrave.net", "https://www.zdrave.net/news.xml", category = SourceCategory.PROFESSIONAL),
        NewsSource("sega", "Сега", "https://www.segabg.com/rss"),
        NewsSource("toest", "Тоест", "https://www.toest.bg/rss/"),
        NewsSource("vesti", "Vesti.bg", "https://www.vesti.bg/rss", enabled = false),
        NewsSource("actualno", "Actualno", "https://www.actualno.com/rss/novini-new", enabled = false)
    )

    /** Dutch description and language per built-in source (shown in the Sources screen). */
    val DESCRIPTIONS: Map<String, Pair<String, String>> = mapOf(
        "dw" to ("Deutsche Welle, Duitse publieke omroep, wereldnieuws" to "en"),
        "aljazeera" to ("Al Jazeera, internationaal nieuws vanuit het Midden-Oosten" to "en"),
        "reuters" to ("Reuters, internationaal persbureau (via Google Nieuws)" to "en"),
        "apnews" to ("Associated Press, Amerikaans persbureau (via Google Nieuws)" to "en"),
        "bbc_world" to ("BBC, Britse publieke omroep, wereldnieuws" to "en"),
        "guardian_world" to ("The Guardian, Britse krant, wereldnieuws" to "en"),
        "france24" to ("France 24, Franse publieke zender, wereldnieuws" to "en"),
        "nos" to ("NOS, Nederlandse publieke omroep, algemeen nieuws" to "nl"),
        "nos_binnenland" to ("NOS, binnenlands nieuws" to "nl"),
        "nu_algemeen" to ("NU.nl, algemeen Nederlands nieuws" to "nl"),
        "nu_gezondheid" to ("NU.nl, gezondheidsnieuws" to "nl"),
        "ggznieuws" to ("GGZ Nieuws, vakblad voor de ggz" to "nl"),
        "skipr" to ("Skipr, nieuws over zorgbeleid en -bestuur (deels betaald)" to "nl"),
        "zorgvisie" to ("Zorgvisie, nieuws over zorgmanagement (deels betaald)" to "nl"),
        "trimbos_news" to ("Trimbos-instituut, kenniscentrum voor ggz, alcohol en drugs" to "nl"),
        "nursing_nl" to ("Nursing, vakblad voor verpleegkundigen" to "nl"),
        "zorgwelzijn" to ("ZorgWelzijn, vakblad voor zorg en welzijn" to "nl"),
        "igj" to ("Inspectie Gezondheidszorg en Jeugd, nieuws van de toezichthouder" to "nl"),
        "igj_toezicht" to ("IGJ, nieuwe toezichtdocumenten en rapporten" to "nl"),
        "gn_nispa" to ("NISPA, Nederlands Instituut voor Seksualiteit, Psychiatrie en Verslaving" to "nl"),
        "gn_ggzstandaarden" to ("GGZ Standaarden, zorgstandaarden voor de ggz" to "nl"),
        "gn_venvn" to ("V&VN, beroepsvereniging van verpleegkundigen" to "nl"),
        "gn_nl_top" to ("Google Nieuws, belangrijkste Nederlandse nieuws" to "nl"),
        "dnevnik" to ("Dnevnik (Дневник), Bulgaarse krant, nieuws en economie" to "bg"),
        "novinite" to ("Novinite, Engelstalig nieuws over Bulgarije" to "en"),
        "bta_bg" to ("BTA, Bulgaars staatspersbureau, binnenlands nieuws" to "bg"),
        "bta_free" to ("BTA, Bulgaars staatspersbureau, vrij nieuws" to "bg"),
        "fakti" to ("Fakti (Факти), Bulgaarse nieuwssite" to "bg"),
        "fakti_bg" to ("Fakti, nieuws uit Bulgarije" to "bg"),
        "gn_bnr" to ("BNR, Bulgaarse publieke radio (via Google Nieuws)" to "bg"),
        "gn_bnt" to ("BNT, Bulgaarse publieke televisie" to "bg"),
        "gn_nova" to ("NOVA, Bulgaarse tv-zender, nieuws" to "bg"),
        "gn_mediapool" to ("Mediapool (Медиапул), onafhankelijke Bulgaarse nieuwssite" to "bg"),
        "gn_24chasa" to ("24 Chasa (24 часа), Bulgaarse dagkrant" to "bg"),
        "gn_darik" to ("Darik (Дарик), Bulgaarse radio, nieuws" to "bg"),
        "clinica_bg" to ("Clinica.bg, Bulgaars gezondheidsnieuws en zorgbeleid" to "bg"),
        "zdrave_net" to ("Zdrave.net, Bulgaars gezondheidsnieuws" to "bg"),
        "sega" to ("Sega (Сега), Bulgaarse krant, nieuws en politiek" to "bg"),
        "toest" to ("Toest (Тоест), Bulgaars onderzoeksjournalistiek en cultuur" to "bg"),
        "vesti" to ("Vesti.bg, Bulgaarse nieuwssite" to "bg"),
        "actualno" to ("Actualno, Bulgaarse nieuwssite" to "bg"),
        "hackernews" to ("Hacker News, technologie en programmeren" to "en"),
        "xda" to ("XDA Developers, Android en telefoons" to "en"),
        "xda_news" to ("XDA Developers, nieuws" to "en"),
        "busfreaks" to ("Busfreaks, Duits forum over bussen (ervaringen)" to "de"),
        "gnews_search_en" to ("Google News, Engelstalig zoeken op je trefwoorden" to "en"),
        "gnews_search_bg" to ("Google Новини, Bulgaars zoeken op je trefwoorden" to "bg"),
        "gnews_bg_top" to ("Google Новини, belangrijkste Bulgaarse nieuws" to "bg"),
        "gnews_search_nl" to ("Google Nieuws, Nederlands zoeken op je trefwoorden" to "nl")
    )

    private val PROFESSIONAL_IDS = setOf("ggznieuws", "skipr", "zorgvisie", "trimbos_news")

    /** Adds the Dutch description and language to built-in sources that do not have them yet. */
    fun describe(source: NewsSource): NewsSource {
        val (desc, lang) = DESCRIPTIONS[source.id] ?: return source
        return source.copy(
            description = source.description.ifBlank { desc },
            language = source.language.ifBlank { lang },
            category = if (source.id in PROFESSIONAL_IDS && source.category == SourceCategory.NEWS) SourceCategory.PROFESSIONAL else source.category
        )
    }

    const val SOURCES_VERSION = 6

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
        language = "nl",
        includeResearch = true
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
        sources = (sources + freeSources + dutchSources + sourcesV3 + sourcesV4 + sourcesV5 + sourcesV6)
            .map { s -> updatesV6[s.id]?.let { (n, u) -> s.copy(name = n, url = u) } ?: s }
            .map { describe(it) },
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
                (if (result.sourcesVersion < 3) sourcesV3 else emptyList()) +
                (if (result.sourcesVersion < 4) sourcesV4 else emptyList()) +
                (if (result.sourcesVersion < 5) sourcesV5 else emptyList()) + sourcesV6
            val updated = if (result.sourcesVersion < 6) {
                result.sources.map { s -> updatesV6[s.id]?.let { (n, u) -> s.copy(name = n, url = u) } ?: s }
            } else result.sources
            result = result.copy(
                sources = (updated + added.filterNot { it.id in existingIds }).map { describe(it) },
                sourcesVersion = SOURCES_VERSION
            )
        }
        return result
    }
}
