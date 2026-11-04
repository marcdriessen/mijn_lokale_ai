package com.example.auralocalai.data.medical

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** One publication from Europe PMC. */
data class EuropePmcArticle(
    val id: String,
    val pmid: String,
    val doi: String,
    val title: String,
    val abstract: String,
    val journal: String,
    val year: String,
    val isOpenAccess: Boolean,
    val publicationTypes: List<String>,
    val fullTextUrl: String
) {
    /** Link to the article page (Europe PMC shows the abstract and, for open access, the full text). */
    val url: String
        get() = when {
            pmid.isNotBlank() -> "https://europepmc.org/article/MED/$pmid"
            doi.isNotBlank() -> "https://doi.org/$doi"
            else -> fullTextUrl
        }
}

/**
 * Free Europe PMC REST search (no key). Adds European and open-access literature next to PubMed.
 * Only the search terms leave the device.
 */
class EuropePmcClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()
) {
    suspend fun search(query: String, maxResults: Int = 4): List<EuropePmcArticle> = withContext(Dispatchers.IO) {
        val url = "https://www.ebi.ac.uk/europepmc/webservices/rest/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("format", "json")
            .addQueryParameter("resultType", "core")
            .addQueryParameter("pageSize", maxResults.toString())
            .build()
        val request = Request.Builder().url(url).header("User-Agent", "AuraLocalAI/1.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Europe PMC HTTP ${response.code}")
            EuropePmcParser.parse(response.body?.string().orEmpty())
        }
    }
}

/** Pure parsing of the Europe PMC JSON (unit-testable). */
object EuropePmcParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): List<EuropePmcArticle> {
        if (body.isBlank()) return emptyList()
        val root = json.parseToJsonElement(body).jsonObject
        val results = root["resultList"]?.jsonObject?.get("result") as? JsonArray ?: return emptyList()
        return results.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            fun s(key: String) = (o[key]?.jsonPrimitive?.contentOrNull).orEmpty()
            val title = clean(s("title"))
            if (title.isBlank()) return@mapNotNull null
            val journal = o["journalInfo"]?.jsonObject?.get("journal")?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull
                ?: s("journalTitle")
            val types = (o["pubTypeList"]?.jsonObject?.get("pubType") as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
            val fullText = (o["fullTextUrlList"]?.jsonObject?.get("fullTextUrl") as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull }
                ?.firstOrNull().orEmpty()
            EuropePmcArticle(
                id = s("id"),
                pmid = s("pmid"),
                doi = s("doi"),
                title = title,
                abstract = clean(s("abstractText")),
                journal = clean(journal),
                year = s("pubYear").ifBlank { s("firstPublicationDate").take(4) },
                isOpenAccess = s("isOpenAccess") == "Y",
                publicationTypes = types,
                fullTextUrl = fullText
            )
        }
    }

    private fun clean(text: String): String =
        text.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

    /** Normalised title, used to recognise the same publication from PubMed and Europe PMC. */
    fun titleKey(title: String): String = title.lowercase().filter { it.isLetterOrDigit() }.take(80)
}
