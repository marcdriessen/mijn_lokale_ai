package com.example.auralocalai.data.briefing

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Small, dependency-free parser for RSS 2.0, RSS 1.0 (RDF) and Atom feeds.
 * Pure Kotlin so it can be unit-tested on the JVM.
 */
object RssParser {

    private val ITEM_REGEX = Regex("<(item|entry)\\b[^>]*>(.*?)</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val CDATA_REGEX = Regex("<!\\[CDATA\\[(.*?)]]>", RegexOption.DOT_MATCHES_ALL)
    private val TAG_REGEX = Regex("<[^>]+>")
    /** Google News: <source url="https://www.example.com">Example</source> names the real publisher. */
    private val SOURCE_URL_REGEX = Regex("<source\\b[^>]*url\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val NUMERIC_ENTITY_REGEX = Regex("&#(x?[0-9a-fA-F]+);")

    fun parse(xml: String, sourceName: String): List<NewsItem> {
        return ITEM_REGEX.findAll(xml).mapNotNull { match ->
            val body = match.groupValues[2]
            val title = cleanText(firstTag(body, "title") ?: return@mapNotNull null)
            if (title.isBlank()) return@mapNotNull null
            val link = extractLink(body) ?: ""
            val description = cleanText(
                firstTag(body, "description") ?: firstTag(body, "summary") ?: firstTag(body, "content") ?: ""
            ).take(500)
            val dateText = firstTag(body, "pubDate") ?: firstTag(body, "dc:date")
                ?: firstTag(body, "published") ?: firstTag(body, "updated")
            NewsItem(
                title = title,
                link = link,
                sourceName = sourceName,
                publishedMillis = dateText?.let { parseDate(cleanText(it)) },
                description = description,
                imageUrl = extractImage(body),
                publisherDomain = SOURCE_URL_REGEX.find(body)?.groupValues?.get(1)?.let { Paywall.domainOf(it) }.orEmpty()
            )
        }.toList()
    }

    private fun firstTag(body: String, tag: String): String? {
        val regex = Regex("<${Regex.escape(tag)}\\b[^>]*>(.*?)</${Regex.escape(tag)}>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        return regex.find(body)?.groupValues?.get(1)
    }

    private fun extractLink(body: String): String? {
        // RSS: <link>https://...</link>
        firstTag(body, "link")?.let { text ->
            val cleaned = cleanText(text)
            if (cleaned.startsWith("http")) return cleaned
        }
        // Atom: <link href="..." rel="alternate"/> (prefer alternate, else first)
        val linkTags = Regex("<link\\b([^>]*)/?>", RegexOption.IGNORE_CASE).findAll(body).map { it.groupValues[1] }.toList()
        val hrefRegex = Regex("href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        val alternate = linkTags.firstOrNull { !it.contains("rel=", ignoreCase = true) || it.contains("alternate", ignoreCase = true) }
        val chosen = alternate ?: linkTags.firstOrNull()
        chosen?.let { attrs -> hrefRegex.find(attrs)?.groupValues?.get(1)?.let { return decodeEntities(it) } }
        // Fallback: <guid>https://...</guid>
        firstTag(body, "guid")?.let { text ->
            val cleaned = cleanText(text)
            if (cleaned.startsWith("http")) return cleaned
        }
        return null
    }

    private val MEDIA_REGEX = Regex("<media:(?:content|thumbnail)\\b[^>]*url\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE)
    private val ENCLOSURE_REGEX = Regex("<enclosure\\b([^>]*)>", RegexOption.IGNORE_CASE)
    private val IMG_REGEX = Regex("<img\\b[^>]*src\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    /** Image of an item: media:content/thumbnail, an image enclosure, or the first <img> in the text. */
    fun extractImage(body: String): String? {
        MEDIA_REGEX.find(body)?.groupValues?.get(1)?.let { return decodeEntities(it) }
        ENCLOSURE_REGEX.findAll(body).forEach { m ->
            val attrs = m.groupValues[1]
            if (attrs.contains("image", ignoreCase = true)) {
                Regex("url\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)?.let { return decodeEntities(it) }
            }
        }
        val decoded = decodeEntities(CDATA_REGEX.replace(body) { it.groupValues[1] })
        return IMG_REGEX.find(decoded)?.groupValues?.get(1)?.takeIf { it.startsWith("http") }
    }

    /** Removes CDATA, HTML tags and entities, and collapses whitespace. */
    fun cleanText(raw: String): String {
        var text = CDATA_REGEX.replace(raw) { it.groupValues[1] }
        text = decodeEntities(text) // entities first: descriptions often contain escaped HTML
        text = TAG_REGEX.replace(text, " ")
        text = decodeEntities(text)
        return WHITESPACE_REGEX.replace(text, " ").trim()
    }

    fun decodeEntities(text: String): String {
        var result = text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
        result = NUMERIC_ENTITY_REGEX.replace(result) { m ->
            val value = m.groupValues[1]
            val code = if (value.startsWith("x") || value.startsWith("X")) value.drop(1).toIntOrNull(16) else value.toIntOrNull()
            if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else m.value
        }
        return result.replace("&amp;", "&")
    }

    private val DATE_PATTERNS = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, d MMM yyyy HH:mm:ss Z",
        "EEE, d MMM yyyy HH:mm:ss zzz",
        "dd MMM yyyy HH:mm:ss Z",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    )

    fun parseDate(text: String): Long? {
        val trimmed = text.trim()
        for (pattern in DATE_PATTERNS) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)
                format.isLenient = false
                if (pattern.endsWith("'Z'")) format.timeZone = TimeZone.getTimeZone("UTC")
                val date = format.parse(trimmed)
                if (date != null) return date.time
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return null
    }
}
