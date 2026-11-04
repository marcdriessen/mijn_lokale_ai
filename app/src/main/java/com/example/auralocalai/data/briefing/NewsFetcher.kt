package com.example.auralocalai.data.briefing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Result of fetching one source: the items, or the error message. */
data class SourceFetchResult(
    val source: NewsSource,
    val items: List<NewsItem>,
    val error: String? = null
)

/**
 * Downloads RSS/Atom feeds. Only the feed URLs (and, for search feeds, the topic keywords)
 * leave the device; no conversation data is sent.
 */
class NewsFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    /** Fetches every enabled source. Search feeds are queried once per keyword in [searchKeywords]. */
    suspend fun fetchAll(sources: List<NewsSource>, searchKeywords: List<String>): List<SourceFetchResult> = coroutineScope {
        sources.filter { it.enabled }.map { source ->
            async(Dispatchers.IO) { fetchSource(source, searchKeywords) }
        }.awaitAll()
    }

    suspend fun fetchSource(source: NewsSource, searchKeywords: List<String> = emptyList()): SourceFetchResult =
        withContext(Dispatchers.IO) {
            try {
                val urls = if (source.isSearchFeed) {
                    searchKeywords.take(MAX_SEARCH_QUERIES).map { keyword ->
                        source.url.replace(NewsSource.SEARCH_PLACEHOLDER, URLEncoder.encode(keyword, "UTF-8"))
                    }
                } else {
                    listOf(source.url)
                }
                val items = urls.flatMap { url -> RssParser.parse(download(url), source.name) }
                    .map { if (source.category == SourceCategory.NEWS) it else it.copy(sourceCategory = source.category) }
                SourceFetchResult(source, items)
            } catch (e: Exception) {
                SourceFetchResult(source, emptyList(), e.message ?: e.javaClass.simpleName)
            }
        }

    /**
     * Finds a preview image for an article page (og:image / twitter:image). Only reads the start
     * of the page. Returns null for Google News redirect links or on any error.
     */
    suspend fun fetchPreviewImage(articleUrl: String): String? = withContext(Dispatchers.IO) {
        if (articleUrl.isBlank() || articleUrl.contains("news.google.com")) return@withContext null
        try {
            val request = Request.Builder()
                .url(articleUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val source = response.body?.source() ?: return@withContext null
                source.request(MAX_HTML_BYTES)
                val html = source.buffer.snapshot().utf8().take(MAX_HTML_BYTES.toInt())
                OG_IMAGE_REGEXES.firstNotNullOfOrNull { regex -> regex.find(html)?.groupValues?.get(1) }
                    ?.let { RssParser.decodeEntities(it) }
                    ?.takeIf { it.startsWith("http") }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Picture and readable text of an article page. */
    data class ArticlePage(val imageUrl: String?, val text: String, val access: ArticleContent.Access = ArticleContent.Access.FREE)

    /**
     * Downloads an article page once and returns both its preview image and its main text.
     * Google News redirect links cannot be read this way (null).
     */
    suspend fun fetchArticle(articleUrl: String, maxChars: Int = 6_000): ArticlePage? = withContext(Dispatchers.IO) {
        if (articleUrl.isBlank() || articleUrl.contains("news.google.com")) return@withContext null
        try {
            val builder = Request.Builder()
                .url(articleUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html")
            // Logged in to this site in the app's browser: send the session along (subscribed articles).
            WebLogins.cookieFor(articleUrl)?.let { builder.header("Cookie", it) }
            val request = builder.build()
            client.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 402 || response.code == 403 || response.code == 429) {
                    return@withContext ArticlePage(null, "", if (response.code == 402) ArticleContent.Access.PAYWALL else ArticleContent.Access.BLOCKED)
                }
                if (!response.isSuccessful) return@withContext null
                val source = response.body?.source() ?: return@withContext null
                source.request(MAX_ARTICLE_BYTES)
                val html = source.buffer.snapshot().utf8().take(MAX_ARTICLE_BYTES.toInt())
                val image = OG_IMAGE_REGEXES.firstNotNullOfOrNull { regex -> regex.find(html)?.groupValues?.get(1) }
                    ?.let { RssParser.decodeEntities(it) }
                    ?.takeIf { it.startsWith("http") }
                val text = com.example.auralocalai.data.translate.PageTextExtractor.extract(html, maxChars = maxChars).text
                ArticlePage(image, text, ArticleContent.access(html, text))
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * For a source the user adds: when the address is a web page instead of a feed, returns the feed
     * the page announces (rel="alternate"). Returns the address itself when it already is a feed.
     */
    suspend fun discoverFeed(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val body = download(url)
            when {
                ArticleContent.looksLikeFeed(body) -> url
                else -> ArticleContent.detectFeeds(body, url).firstOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun download(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.5")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IllegalStateException("Empty response")
            return if (body.length > MAX_FEED_CHARS) body.substring(0, MAX_FEED_CHARS) else body
        }
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Android) AuraLocalAI-Briefing/1.0"
        const val MAX_SEARCH_QUERIES = 12
        const val MAX_FEED_CHARS = 2_000_000
        const val MAX_HTML_BYTES = 300_000L
        const val MAX_ARTICLE_BYTES = 800_000L
        private val OG_IMAGE_REGEXES = listOf(
            Regex("<meta[^>]+property=[\"']og:image(?::url)?[\"'][^>]+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE),
            Regex("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image(?::url)?[\"']", RegexOption.IGNORE_CASE),
            Regex("<meta[^>]+name=[\"']twitter:image[\"'][^>]+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        )
    }
}
