package com.example.auralocalai.data.briefing

import com.example.auralocalai.data.translate.NewsTranslator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Builds a complete briefing in one go (used by the screen and by the morning background job):
 * 1. fetch news (RSS, plus optional extra items such as web search results),
 * 2. pick articles per topic,
 * 3. find preview images,
 * 4. translate headline + teaser offline (ML Kit) into the briefing language,
 * 5. optionally let the local model write title, summary and "what this means for your work".
 * The result is only returned when everything is done.
 */
class BriefingGenerator(
    private val repository: BriefingRepository,
    private val fetcher: NewsFetcher = NewsFetcher(),
    private val translator: NewsTranslator? = null,
    /** Looks up a study by DOI (Europe PMC / Crossref); null = no lookups. */
    private val studyLookup: (suspend (String) -> LinkedStudy?)? = null
) {

    enum class Stage { FETCHING, RANKING, IMAGES, FULLTEXT, TRANSLATING, WRITING }

    data class Progress(val stage: Stage, val done: Int = 0, val total: Int = 0)

    /** One prompt to the local model; returns the raw answer or null. */
    fun interface ModelCall {
        suspend fun run(systemPrompt: String, prompt: String): String?
    }

    suspend fun build(
        profile: BriefingProfile,
        config: BriefingConfig,
        languageCode: String,
        languageName: String,
        forceRefresh: Boolean,
        extraItems: List<NewsItem> = emptyList(),
        model: ModelCall? = null,
        /** Stop calling the model after this moment (epoch millis), e.g. to stay within a background time limit. */
        modelDeadline: Long = Long.MAX_VALUE,
        /** Read and translate the whole articles in advance (stored with the briefing). */
        prepareFullText: Boolean = true,
        onProgress: (Progress) -> Unit = {},
        onSourceErrors: (Map<String, String>) -> Unit = {}
    ): Briefing {
        val topics = profile.topics.filter { it.enabled }
        onProgress(Progress(Stage.FETCHING))
        val items = mutableListOf<NewsItem>()
        if (profile.mode != SourceMode.WEB) items += loadRssItems(config, topics, forceRefresh, onSourceErrors)
        items += extraItems
        // Paid sites are skipped, unless the user logged in to that site in the app's browser.
        if (config.skipPaywalled) {
            val loginCache = HashMap<String, Boolean>()
            fun loggedIn(domain: String) = domain.isNotBlank() && loginCache.getOrPut(domain) { WebLogins.hasLogin(domain) }
            items.removeAll { Paywall.isPaywalled(it) && !loggedIn(Paywall.domainOf(it.link)) && !loggedIn(it.publisherDomain) }
        }

        // Tier 1: keywords (round-robin over topics). Tier 2: the local model ranks the candidates
        // in one short call; without a model or time the keyword order is kept.
        val max = profile.maxArticles.coerceIn(1, 15)
        val candidates = TopicMatcher.selectArticles(items, topics, (max * 2).coerceAtMost(30))
        val selected = if (model != null && candidates.size > max && System.currentTimeMillis() < modelDeadline) {
            onProgress(Progress(Stage.RANKING))
            val raw = runCatching {
                model.run(
                    NewsIntelligence.RELEVANCE_SYSTEM,
                    NewsIntelligence.relevancePrompt(profile.instructions, topics.map { it.name }, candidates.map { it.first })
                )
            }.getOrNull()
            NewsIntelligence.rankByScores(candidates, NewsIntelligence.parseScores(raw, candidates.size)).take(max)
        } else candidates.take(max)
        val now = System.currentTimeMillis()
        // "All results": every other matching item, newest first, so nothing disappears unseen.
        val chosenLinks = selected.map { it.first.link }.toSet()
        val moreItems = topics.flatMap { TopicMatcher.selectForTopic(items, it, MAX_MORE_ITEMS) }
            .filter { it.link !in chosenLinks }
            .distinctBy { it.link.ifBlank { it.title } }
            .sortedByDescending { it.publishedMillis ?: 0L }
            .take(MAX_MORE_ITEMS)
            .map { it.copy(content = "") }
        var articles = selected.map { (item, topic) ->
            BriefingArticle(item, topic.name, related = NewsIntelligence.relatedReports(item, items).map { it.copy(content = "") })
        }

        if (articles.isNotEmpty()) {
            // Preview images for articles whose feed had none.
            // Full article text (temporary, only for the model) and a picture when the feed had none.
            // Text from the feed itself (content:encoded) is used first; otherwise the article page is read.
            onProgress(Progress(Stage.IMAGES))
            // Google News links: find the real publisher address first (so the article can be read).
            articles = coroutineScope {
                articles.map { a ->
                    async {
                        if (!GoogleNewsResolver.isGoogleNews(a.item.link)) a
                        else {
                            val real = runCatching { GoogleNewsResolver.resolve(a.item.link) }.getOrNull()
                            if (real != null) a.copy(item = a.item.copy(link = real, publisherDomain = Paywall.domainOf(real))) else a
                        }
                    }
                }.awaitAll()
            }
            // Read each article. When it is behind a paywall, blocked or unreadable, look for the same
            // news at another (free) source and use that one instead.
            val pool = items.filterNot { Paywall.isPaywalled(it) }
            val readResults = coroutineScope {
                articles.map { article ->
                    async {
                        val feedText = article.item.content.takeIf { ArticleContent.isUsable(it) }
                        if (feedText != null) {
                            return@async Triple(article, NewsFetcher.ArticlePage(article.item.imageUrl, feedText), false)
                        }
                        val page = runCatching { fetcher.fetchArticle(article.item.link) }.getOrNull()
                        val readable = page != null && page.access == ArticleContent.Access.FREE && ArticleContent.isUsable(page.text)
                        if (readable) return@async Triple(article, page!!, false)
                        val alternative = findFreeAlternative(article.item, pool)
                        if (alternative != null) {
                            val (altItem, altPage) = alternative
                            val replaced = article.copy(
                                item = altItem.copy(imageUrl = altItem.imageUrl ?: article.item.imageUrl ?: page?.imageUrl),
                                related = (listOf(article.item) + article.related).filter { it.link != altItem.link }.take(4),
                                replacedSource = article.item.sourceName
                            )
                            Triple(replaced, altPage, false)
                        } else {
                            val blocked = page?.access == ArticleContent.Access.PAYWALL || page?.access == ArticleContent.Access.BLOCKED
                            Triple(article, NewsFetcher.ArticlePage(article.item.imageUrl ?: page?.imageUrl, ""), blocked)
                        }
                    }
                }.awaitAll()
            }
            val pages = readResults.map { it.second }
            val fullTexts = pages.map { it.text }
            articles = readResults.mapIndexed { i, (a, _, paywalled) ->
                a.copy(
                    item = a.item.copy(imageUrl = pages[i].imageUrl ?: a.item.imageUrl, content = ""),
                    textStatus = when {
                        fullTexts[i].isNotBlank() && a.item.content.isNotBlank() && ArticleContent.isUsable(a.item.content) -> ArticleContent.STATUS_FEED
                        fullTexts[i].isNotBlank() -> ArticleContent.STATUS_FULL
                        paywalled -> ArticleContent.STATUS_PAYWALL
                        else -> ArticleContent.STATUS_SUMMARY_ONLY
                    }
                )
            }

            // Prepare the whole article in advance, translated into the briefing language, so it can be read
            // immediately when opened (also for the scheduled briefing made before the chosen time).
            val fullTranslator = translator
            if (fullTranslator != null && prepareFullText) {
                articles = articles.mapIndexed { i, a ->
                    val paragraphs = fullTexts[i].split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotBlank() }.take(MAX_FULL_PARAGRAPHS)
                    if (paragraphs.isEmpty()) return@mapIndexed a
                    onProgress(Progress(Stage.FULLTEXT, i + 1, articles.size))
                    val translated = paragraphs.map { p ->
                        FullParagraph(runCatching { fullTranslator.translate(p, languageCode) }.getOrNull() ?: p, p)
                    }
                    a.copy(fullText = translated)
                }
            }

            // Research news: find the underlying study via its DOI (Europe PMC, then Crossref).
            val lookup = studyLookup
            if (lookup != null) {
                articles = articles.mapIndexed { i, a ->
                    val doi = NewsIntelligence.findDoi(fullTexts[i], a.item.link, a.item.description) ?: return@mapIndexed a
                    val study = runCatching { lookup(doi) }.getOrNull()
                    if (study != null) a.copy(study = study) else a
                }
            }

            // Offline translation of headline and teaser.
            if (translator != null) {
                articles = articles.mapIndexed { i, a ->
                    onProgress(Progress(Stage.TRANSLATING, i + 1, articles.size))
                    val title = translator.translate(a.item.title, languageCode)
                    val teaser = a.item.description.takeIf { it.isNotBlank() }?.let { translator.translate(it.take(600), languageCode) }
                    if (title != null || teaser != null) {
                        a.copy(
                            title = title ?: a.item.title,
                            summary = teaser ?: a.item.description,
                            processed = true
                        )
                    } else a
                }
            }

            // Local model: title, summary and relevance, based on the (translated) text.
            if (model != null) {
                val systemPrompt = BriefingPrompts.articleSystemPrompt(languageName, profile.instructions)
                articles = articles.mapIndexed { i, a ->
                    if (System.currentTimeMillis() > modelDeadline) return@mapIndexed a
                    onProgress(Progress(Stage.WRITING, i + 1, articles.size))
                    val input = a.item.copy(
                        title = a.title.ifBlank { a.item.title },
                        description = a.summary.ifBlank { a.item.description }
                    )
                    val raw = model.run(systemPrompt, BriefingPrompts.articlePrompt(input, a.topicName, languageName, fullTexts[i], a.study, a.item.sourceCategory))
                    if (raw.isNullOrBlank()) return@mapIndexed a
                    val parsed = BriefingPrompts.parseArticle(raw)
                    a.copy(
                        title = parsed.title.ifBlank { a.title },
                        summary = parsed.summary.ifBlank { a.summary },
                        relevance = parsed.relevance,
                        processed = true
                    )
                }
            }
        }

        return Briefing(
            id = now.toString(),
            createdAt = System.currentTimeMillis(),
            language = languageCode,
            profileId = profile.id,
            profileName = profile.name,
            articles = articles,
            moreItems = moreItems
        )
    }

    /**
     * The same news at another, freely readable source: similar headline, different publisher, not on
     * the paywall list. Tries up to 4 candidates and returns the first one whose full text can be read.
     */
    private suspend fun findFreeAlternative(item: NewsItem, pool: List<NewsItem>): Pair<NewsItem, NewsFetcher.ArticlePage>? {
        val candidates = NewsIntelligence.relatedReports(item, pool, threshold = 0.3, max = 4)
        for (candidate in candidates) {
            val link = if (GoogleNewsResolver.isGoogleNews(candidate.link)) {
                runCatching { GoogleNewsResolver.resolve(candidate.link) }.getOrNull() ?: continue
            } else candidate.link
            if (Paywall.isPaywalled(candidate.copy(link = link))) continue
            val feedText = candidate.content.takeIf { ArticleContent.isUsable(it) }
            if (feedText != null) return candidate.copy(link = link) to NewsFetcher.ArticlePage(candidate.imageUrl, feedText)
            val page = runCatching { fetcher.fetchArticle(link) }.getOrNull() ?: continue
            if (page.access == ArticleContent.Access.FREE && ArticleContent.isUsable(page.text)) {
                return candidate.copy(link = link, publisherDomain = Paywall.domainOf(link)) to page
            }
        }
        return null
    }

    private suspend fun loadRssItems(
        config: BriefingConfig,
        topics: List<BriefingTopic>,
        forceRefresh: Boolean,
        onSourceErrors: (Map<String, String>) -> Unit
    ): List<NewsItem> {
        val cache = withContext(Dispatchers.IO) { repository.loadCache() }
        val cacheIsFresh = System.currentTimeMillis() - cache.fetchedAt < CACHE_MAX_AGE_MS && cache.items.isNotEmpty()
        if (!forceRefresh && cacheIsFresh) return cache.items

        val keywords = topics.flatMap { it.allKeywords }.distinctBy { it.lowercase() }
        val results = fetcher.fetchAll(config.sources, keywords)
        withContext(Dispatchers.IO) { runCatching { repository.saveSourceResults(results) } }
        val errors = results.filter { it.error != null }.associate { it.source.id to (it.error ?: "") }
        if (errors.isNotEmpty()) onSourceErrors(errors)
        val items = results.flatMap { it.items }
        if (items.isNotEmpty()) {
            withContext(Dispatchers.IO) { repository.saveCache(FetchCache(System.currentTimeMillis(), items)) }
        }
        return items
    }

    companion object {
        const val CACHE_MAX_AGE_MS = 3L * 60L * 60L * 1000L
        const val MAX_MORE_ITEMS = 60
        const val MAX_FULL_PARAGRAPHS = 60

        /** Language the briefing is written in: the profile's choice, Dutch for the care briefing, else the app language. */
        fun languageFor(profile: BriefingProfile, appLanguage: String): String =
            profile.language.takeIf { it in listOf("nl", "en", "bg") }
                ?: if (profile.id == DefaultBriefing.CARE_PROFILE_ID && profile.language.isEmpty()) "nl" else appLanguage
    }
}
