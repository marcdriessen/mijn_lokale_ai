package com.example.auralocalai.data.medical

import com.example.auralocalai.data.briefing.LinkedStudy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Finds the study behind a DOI: first Europe PMC (title, journal, year, publication type),
 * then Crossref (metadata only). Only the DOI leaves the device.
 */
object StudyLookup {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
    private val europePmc = EuropePmcClient(client)

    suspend fun lookup(doi: String): LinkedStudy? {
        val clean = doi.trim().removePrefix("https://doi.org/")
        runCatching { europePmc.search("DOI:\"$clean\"", maxResults = 1).firstOrNull() }.getOrNull()?.let { a ->
            return LinkedStudy(a.title, a.journal, a.year, a.url, a.publicationTypes.firstOrNull().orEmpty())
        }
        return runCatching { crossref(clean) }.getOrNull()
    }

    private suspend fun crossref(doi: String): LinkedStudy? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.crossref.org/works/" + java.net.URLEncoder.encode(doi, "UTF-8").replace("%2F", "/"))
            .header("User-Agent", "AuraLocalAI/1.0 (offline assistant)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            CrossrefParser.parse(response.body?.string().orEmpty(), doi)
        }
    }
}

/** Pure parsing of a Crossref /works/{doi} response (unit-testable). */
object CrossrefParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String, doi: String): LinkedStudy? {
        if (body.isBlank()) return null
        val message = json.parseToJsonElement(body).jsonObject["message"] as? JsonObject ?: return null
        fun first(key: String) = (message[key] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.contentOrNull.orEmpty()
        val title = first("title").replace(Regex("<[^>]+>"), "").trim()
        if (title.isBlank()) return null
        val year = listOf("published-print", "published-online", "issued").firstNotNullOfOrNull { key ->
            ((message[key] as? JsonObject)?.get("date-parts") as? JsonArray)
                ?.firstOrNull()?.let { it as? JsonArray }?.firstOrNull()?.jsonPrimitive?.intOrNull
        }
        return LinkedStudy(
            title = title,
            journal = first("container-title"),
            year = year?.toString().orEmpty(),
            url = "https://doi.org/$doi",
            type = message["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
        )
    }
}
