package com.example.auralocalai.data.providers

import com.example.auralocalai.data.SearchOutcome
import com.example.auralocalai.data.SearchProvider
import com.example.auralocalai.data.SearchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Web search via a cloud AI that searches the web itself (Gemini with Google Search, or OpenAI
 * with web search). Needs the user's own API key (Google AI Studio / OpenAI platform). Only the
 * search question leaves the phone; private texts are anonymised before any web search.
 */
private val aiClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(90, TimeUnit.SECONDS)
    .callTimeout(120, TimeUnit.SECONDS)
    .build()

private fun aiPrompt(query: String, country: String, language: String): String =
    "Search the web for: $query\n" +
        "Region: $country, language: $language. Prefer recent, reliable sources (official bodies, " +
        "professional and scientific sources, established news media). Give a factual summary of the most " +
        "relevant results with key facts, numbers and dates, and name the source of each fact. " +
        "If the search question contains site: filters, only use those sites."

private fun errorOutcome(query: String, id: String, message: String) =
    SearchOutcome(query = query, sources = emptyList(), providerId = id, isError = true, errorMessage = message)

/** Gemini API (Google AI Studio key) with the google_search tool. */
class GeminiSearchProvider(private val model: () -> String = { DEFAULT_MODEL }) : SearchProvider {
    override val id = "gemini"
    override val label = "Gemini + Google Search"
    override val requiresApiKey = true
    override val isEncyclopedicOnly = false

    override suspend fun search(query: String, apiKey: String, maxResults: Int, country: String, language: String): SearchOutcome =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext errorOutcome(query, id, "Gemini API key is missing.")
            val body = buildJsonObject {
                putJsonArray("contents") {
                    add(buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") { add(buildJsonObject { put("text", aiPrompt(query, country, language)) }) }
                    })
                }
                putJsonArray("tools") { add(buildJsonObject { putJsonObject("google_search") {} }) }
            }
            val modelName = model().ifBlank { DEFAULT_MODEL }
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent")
                .header("x-goog-api-key", apiKey.trim())
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            try {
                aiClient.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    when {
                        resp.code == 400 && text.contains("API key", true) -> errorOutcome(query, id, "Invalid Gemini API key.")
                        resp.code == 401 || resp.code == 403 -> errorOutcome(query, id, "Gemini API key not authorised (${resp.code}).")
                        resp.code == 404 -> errorOutcome(query, id, "Gemini model '$modelName' not found. Change the model name in Settings.")
                        resp.code == 429 -> errorOutcome(query, id, "Gemini quota or rate limit reached (429).")
                        !resp.isSuccessful -> errorOutcome(query, id, "Gemini error: HTTP ${resp.code}")
                        else -> SearchOutcome(query, AiSearchParser.parseGemini(text, maxResults), id)
                    }
                }
            } catch (e: Exception) {
                errorOutcome(query, id, e.message ?: "Network error during Gemini search.")
            }
        }

    companion object {
        /** Alias that always points to Google's current Flash model. */
        const val DEFAULT_MODEL = "gemini-flash-latest"
    }
}

/** OpenAI Responses API (platform.openai.com key, separate from a ChatGPT subscription) with web search. */
class OpenAiSearchProvider(private val model: () -> String = { DEFAULT_MODEL }) : SearchProvider {
    override val id = "openai"
    override val label = "OpenAI web search"
    override val requiresApiKey = true
    override val isEncyclopedicOnly = false

    override suspend fun search(query: String, apiKey: String, maxResults: Int, country: String, language: String): SearchOutcome =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext errorOutcome(query, id, "OpenAI API key is missing.")
            val modelName = model().ifBlank { DEFAULT_MODEL }
            val body = buildJsonObject {
                put("model", modelName)
                putJsonArray("tools") { add(buildJsonObject { put("type", "web_search") }) }
                put("input", aiPrompt(query, country, language))
            }
            val request = Request.Builder()
                .url("https://api.openai.com/v1/responses")
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            try {
                aiClient.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    when {
                        resp.code == 401 || resp.code == 403 -> errorOutcome(query, id, "OpenAI API key not authorised (${resp.code}).")
                        resp.code == 404 -> errorOutcome(query, id, "OpenAI model '$modelName' not found. Change the model name in Settings.")
                        resp.code == 429 -> errorOutcome(query, id, "OpenAI quota or rate limit reached (429). Check your credit.")
                        !resp.isSuccessful -> errorOutcome(query, id, "OpenAI error: HTTP ${resp.code}")
                        else -> SearchOutcome(query, AiSearchParser.parseOpenAi(text, maxResults), id)
                    }
                }
            } catch (e: Exception) {
                errorOutcome(query, id, e.message ?: "Network error during OpenAI search.")
            }
        }

    companion object {
        const val DEFAULT_MODEL = "gpt-5-mini"
    }
}

/** Pure parsing of the AI search answers (unit-tested). The AI's summary comes first, then the cited pages. */
object AiSearchParser {
    private val json = Json { ignoreUnknownKeys = true }
    private const val SUMMARY_TITLE = "AI summary of the search results"

    private fun domainOf(url: String, fallback: String): String =
        fallback.ifBlank { url.substringAfter("://").substringBefore('/').removePrefix("www.") }

    fun parseGemini(body: String, maxResults: Int): List<SearchSource> {
        val candidate = (json.parseToJsonElement(body).jsonObject["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject
            ?: return emptyList()
        val answer = (candidate["content"]?.jsonObject?.get("parts") as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }
            ?.joinToString("").orEmpty().trim()
        val meta = candidate["groundingMetadata"] as? JsonObject
        val chunks = (meta?.get("groundingChunks") as? JsonArray)?.map { c ->
            val web = (c as? JsonObject)?.get("web") as? JsonObject
            (web?.get("uri")?.jsonPrimitive?.contentOrNull.orEmpty()) to (web?.get("title")?.jsonPrimitive?.contentOrNull.orEmpty())
        }.orEmpty()
        // Text segments the answer bases on each chunk become that page's snippet.
        val snippets = Array(chunks.size) { StringBuilder() }
        (meta?.get("groundingSupports") as? JsonArray)?.forEach { s ->
            val o = s as? JsonObject ?: return@forEach
            val segment = (o["segment"] as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
            (o["groundingChunkIndices"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.intOrNull }?.forEach { i ->
                if (i in snippets.indices && snippets[i].length < 600 && segment.isNotBlank()) snippets[i].append(segment).append(' ')
            }
        }
        val pages = chunks.mapIndexedNotNull { i, (uri, title) ->
            if (uri.isBlank()) null else SearchSource(title.ifBlank { domainOf(uri, "") }, uri, snippets[i].toString().trim().ifBlank { title }, domainOf(uri, title))
        }.distinctBy { it.url }.take(maxResults)
        return listOfNotNull(
            answer.takeIf { it.isNotBlank() }?.let { SearchSource(SUMMARY_TITLE, pages.firstOrNull()?.url.orEmpty(), it.take(3_000), "Gemini") }
        ) + pages
    }

    fun parseOpenAi(body: String, maxResults: Int): List<SearchSource> {
        val output = json.parseToJsonElement(body).jsonObject["output"] as? JsonArray ?: return emptyList()
        val texts = StringBuilder()
        val pages = mutableListOf<SearchSource>()
        output.forEach { item ->
            val o = item as? JsonObject ?: return@forEach
            if (o["type"]?.jsonPrimitive?.contentOrNull != "message") return@forEach
            (o["content"] as? JsonArray)?.forEach { c ->
                val part = c as? JsonObject ?: return@forEach
                val text = part["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                texts.append(text)
                (part["annotations"] as? JsonArray)?.forEach { a ->
                    val ann = a as? JsonObject ?: return@forEach
                    if (ann["type"]?.jsonPrimitive?.contentOrNull != "url_citation") return@forEach
                    val url = ann["url"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val title = ann["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val start = ann["start_index"]?.jsonPrimitive?.intOrNull ?: 0
                    val end = ann["end_index"]?.jsonPrimitive?.intOrNull ?: 0
                    // The sentence around the citation is the snippet.
                    val from = text.lastIndexOf('.', (start - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
                    val snippet = text.substring(from.coerceIn(0, text.length), end.coerceIn(0, text.length)).trim()
                    pages += SearchSource(title.ifBlank { domainOf(url, "") }, url.substringBefore("?utm_source=openai"), snippet.ifBlank { title }, domainOf(url, ""))
                }
            }
        }
        val answer = texts.toString().trim()
        val distinct = pages.distinctBy { it.url }.take(maxResults)
        return listOfNotNull(
            answer.takeIf { it.isNotBlank() }?.let { SearchSource(SUMMARY_TITLE, distinct.firstOrNull()?.url.orEmpty(), it.take(3_000), "OpenAI") }
        ) + distinct
    }
}

/** Chosen model names for the AI search services (set from Settings; blank = default). */
object AiSearchSettings {
    @Volatile private var models: Map<String, String> = emptyMap()
    fun set(providerId: String, model: String) { models = models + (providerId to model) }
    fun model(providerId: String): String = models[providerId].orEmpty()
}
