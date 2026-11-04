package com.example.auralocalai.data.translate

import com.example.auralocalai.data.briefing.RssParser

/** Extracts readable text (title + paragraphs) from an HTML page. Pure Kotlin, unit-testable. */
object PageTextExtractor {

    private val DROP_BLOCKS = Regex("<(script|style|noscript|nav|footer|header|aside|form)\\b[^>]*>.*?</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val TITLE = Regex("<title\\b[^>]*>(.*?)</title>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val BLOCKS = Regex("<(p|h1|h2|h3|li)\\b[^>]*>(.*?)</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val URL = Regex("https?://\\S+")

    data class Page(val title: String, val text: String)

    fun extract(html: String, maxChars: Int = 12_000): Page {
        val title = TITLE.find(html)?.groupValues?.get(1)?.let { RssParser.cleanText(it) }.orEmpty()
        val body = DROP_BLOCKS.replace(html, " ")
        val paragraphs = BLOCKS.findAll(body)
            .map { RssParser.cleanText(it.groupValues[2]) }
            .filter { it.length >= 40 }
            .distinct()
            .toList()
        val text = StringBuilder()
        for (p in paragraphs) {
            if (text.length + p.length > maxChars) break
            text.append(p).append("\n\n")
        }
        return Page(title, text.toString().trim())
    }

    /** The first web address in a shared text, if any. */
    fun firstUrl(text: String): String? = URL.find(text)?.value?.trimEnd('.', ',', ')', '"', '\'')
}
