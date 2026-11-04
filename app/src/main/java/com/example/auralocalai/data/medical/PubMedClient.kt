package com.example.auralocalai.data.medical

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Free PubMed search via NCBI E-utilities (no API key needed for light use).
 * Only the search terms leave the device.
 */
class PubMedClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()
) {

    suspend fun search(query: String, maxResults: Int = 5): List<PubMedArticle> = withContext(Dispatchers.IO) {
        val searchUrl = "$BASE/esearch.fcgi".toHttpUrl().newBuilder()
            .addQueryParameter("db", "pubmed")
            .addQueryParameter("term", query)
            .addQueryParameter("retmax", maxResults.toString())
            .addQueryParameter("sort", "relevance")
            .addQueryParameter("retmode", "json")
            .addQueryParameter("tool", TOOL)
            .build()
        val ids = PubMedParser.parseIdList(get(searchUrl.toString()))
        if (ids.isEmpty()) return@withContext emptyList()
        val fetchUrl = "$BASE/efetch.fcgi".toHttpUrl().newBuilder()
            .addQueryParameter("db", "pubmed")
            .addQueryParameter("id", ids.joinToString(","))
            .addQueryParameter("retmode", "xml")
            .addQueryParameter("rettype", "abstract")
            .addQueryParameter("tool", TOOL)
            .build()
        val articles = PubMedParser.parseArticles(get(fetchUrl.toString()))
        // Keep the relevance order of esearch.
        ids.mapNotNull { id -> articles.firstOrNull { it.pmid == id } }
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", "AuraLocalAI/1.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("PubMed HTTP ${response.code}")
            return response.body?.string() ?: ""
        }
    }

    companion object {
        private const val BASE = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils"
        private const val TOOL = "AuraLocalAI"
    }
}
