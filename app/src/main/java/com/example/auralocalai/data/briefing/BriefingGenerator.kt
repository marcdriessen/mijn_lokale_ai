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
    private val translator: NewsTranslator? = null
) {

    enum class Stage { FETCHING, IMAGES, TRANSLATING, WRITING }

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
        onProgress: (Progress) -> Unit = {},
        onSourceErrors: (Map<String, String>) -> Unit = {}
    ): Briefing {
        val topics = profile.topics.filter { it.enabled }
        onProgress(Progress(Stage.FETCHING))
        val items = mutableListOf<NewsItem>()
        if (profile.mode != SourceMode.WEB) items += loadRssItems(config, topics, forceRefresh, onSourceErrors)
        items += extraItems
        if (config.skipPaywalled) items.removeAll { Paywall.isPaywalled(it) }

        val selected = TopicMatcher.selectArticles(items, topics, profile.maxArticles.coerceIn(1, 15))
        val now = System.currentTimeMillis()
        // "All results": every other matching item, newest first, so nothing disappears unseen.
        val chosenLinks = selected.map { it.first.link }.toSet()
        val moreItems = topics.flatMap { TopicMatcher.selectForTopic(items, it, MAX_MORE_ITEMS) }
            .filter { it.link !in chosenLinks }
            .distinctBy { it.link.ifBlank { it.title } }
            .sortedByDescending { it.publishedMillis ?: 0L }
            .take(MAX_MORE_ITEMS)
            .map { it.copy(content = "") }
        var articles = selected.map { (item, topic) -> BriefingArticle(item, topic.name) }

        if (articles.isNotEmpty()) {
            // Preview images for articles whose feed had none.
            // Full article text (temporary, only for the model) and a picture when the feed had none.
            // Text from the feed itself (content:encoded) is used first; otherwise the article page is read.
            onProgress(Progress(Stage.IMAGES))
            val pages = coroutineScope {
                articles.map { article ->
                    async {
                        val feedText = article.item.content.takeIf { ArticleContent.isUsable(it) }
                        if (feedText != null && !article.item.imageUrl.isNullOrBlank()) {
                            NewsFetcher.ArticlePage(article.item.imageUrl, feedText)
                        } else {
                            val page = runCatching { fetcher.fetchArticle(article.item.link) }.getOrNull()
                            NewsFetcher.ArticlePage(
                                article.item.imageUrl?.takeIf { it.isNotBlank() } ?: page?.imageUrl,
                                feedText ?: page?.text?.takeIf { ArticleContent.isUsable(it) }.orEmpty()
                            )
                        }
                    }
                }.awaitAll()
            }
            val fullTexts = pages.map { it.text }
            articles = articles.mapIndexed { i, a ->
                a.copy(
                    item = a.item.copy(imageUrl = pages[i].imageUrl, content = ""),
                    textStatus = when {
                        fullTexts[i].isNotBlank() && a.item.content.isNotBlank() && ArticleContent.isUsable(a.item.content) -> ArticleContent.STATUS_FEED
                        fullTexts[i].isNotBlank() -> ArticleContent.STATUS_FULL
                        else -> ArticleContent.STATUS_SUMMARY_ONLY
                    }
                )
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
                    val raw = model.run(systemPrompt, BriefingPrompts.articlePrompt(input, a.topicName, languageName, fullTexts[i]))
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

        /** Language the briefing is written in: the profile's choice, Dutch for the care briefing, else the app language. */
        fun languageFor(profile: BriefingProfile, appLanguage: String): String =
            profile.language.takeIf { it in listOf("nl", "en", "bg") }
                ?: if (profile.id == DefaultBriefing.CARE_PROFILE_ID && profile.language.isEmpty()) "nl" else appLanguage
    }
}
