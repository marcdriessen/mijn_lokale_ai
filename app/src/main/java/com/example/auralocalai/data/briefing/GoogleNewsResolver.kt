package com.example.auralocalai.data.briefing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Finds the real publisher link behind a Google News RSS link (news.google.com/rss/articles/…),
 * so the app can read the full article and open the publisher directly.
 * Older links contain the address in their base64 id; newer ones are resolved through the same
 * request the Google News website itself makes. When it fails, the Google link is kept.
 */
object GoogleNewsResolver {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    fun isGoogleNews(url: String) = url.contains("news.google.com/") && url.contains("/articles/")

    /** The article id in a Google News link. */
    fun articleId(url: String): String? =
        Regex("/articles/([A-Za-z0-9_-]+)").find(url)?.groupValues?.get(1)

    /** Older ids are base64 and contain the publisher address directly. */
    fun decodeOldFormat(id: String): String? = runCatching {
        val padded = id + "=".repeat((4 - id.length % 4) % 4)
        val bytes = Base64.getUrlDecoder().decode(padded)
        val text = String(bytes, Charsets.ISO_8859_1)
        val start = text.indexOf("http")
        if (start < 0) return@runCatching null
        val url = text.substring(start).takeWhile { it.code in 0x21..0x7E }
        url.takeIf { it.length > 12 && !it.contains("news.google.com") }
    }.getOrNull()

    /** Signature and timestamp from the Google News article page (data-n-a-sg / data-n-a-ts). */
    fun parseSignature(html: String): Pair<String, String>? {
        val sg = Regex("data-n-a-sg=\"([^\"]+)\"").find(html)?.groupValues?.get(1) ?: return null
        val ts = Regex("data-n-a-ts=\"([^\"]+)\"").find(html)?.groupValues?.get(1) ?: return null
        return sg to ts
    }

    fun batchPayload(id: String, timestamp: String, signature: String): String {
        val inner = "[\\\"garturlreq\\\",[[\\\"X\\\",\\\"X\\\",[\\\"X\\\",\\\"X\\\"],null,null,1,1,\\\"US:en\\\",null,1,null,null,null,null,null,0,1]," +
            "\\\"X\\\",\\\"X\\\",1,[1,1,1],1,1,null,0,0,null,0],\\\"$id\\\",$timestamp,\\\"$signature\\\"]"
        return "[[[\"Fbv4je\",\"$inner\",null,\"generic\"]]]"
    }

    /** The decoded address in the batchexecute answer. */
    fun parseBatchResponse(body: String): String? =
        Regex("garturlres\\\\+\",\\\\+\"(https?://[^\"\\\\]+)").find(body)?.groupValues?.get(1)

    suspend fun resolve(url: String): String? = withContext(Dispatchers.IO) {
        if (!isGoogleNews(url)) return@withContext null
        val id = articleId(url) ?: return@withContext null
        decodeOldFormat(id)?.let { return@withContext it }
        runCatching {
            val page = client.newCall(
                Request.Builder().url("https://news.google.com/articles/$id").header("User-Agent", UA).build()
            ).execute().use { it.body?.string().orEmpty() }
            val (signature, timestamp) = parseSignature(page) ?: return@runCatching null
            val body = FormBody.Builder().add("f.req", batchPayload(id, timestamp, signature)).build()
            client.newCall(
                Request.Builder()
                    .url("https://news.google.com/_/DotsSplashUi/data/batchexecute")
                    .header("User-Agent", UA)
                    .post(body)
                    .build()
            ).execute().use { parseBatchResponse(it.body?.string().orEmpty()) }
        }.getOrNull()
    }
}
