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
                SourceFetchResult(source, items)
            } catch (e: Exception) {
                SourceFetchResult(source, emptyList(), e.message ?: e.javaClass.simpleName)
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
    }
}
