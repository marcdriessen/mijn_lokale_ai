package com.example.auralocalai.data.medical

import com.example.auralocalai.data.briefing.RssParser

/** A PubMed record (title + abstract) parsed from an efetch XML response. */
data class PubMedArticle(
    val pmid: String,
    val title: String,
    val abstract: String,
    val journal: String,
    val year: String
) {
    val url: String get() = "https://pubmed.ncbi.nlm.nih.gov/$pmid/"
}

/** Pure-Kotlin parsing of NCBI E-utilities responses (unit-testable). */
object PubMedParser {

    private val ID_REGEX = Regex("\"idlist\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
    private val QUOTED_REGEX = Regex("\"(\\d+)\"")
    private val ARTICLE_REGEX = Regex("<PubmedArticle\\b[^>]*>(.*?)</PubmedArticle>", RegexOption.DOT_MATCHES_ALL)

    /** Reads the PMIDs from an esearch JSON response. */
    fun parseIdList(json: String): List<String> {
        val list = ID_REGEX.find(json)?.groupValues?.get(1) ?: return emptyList()
        return QUOTED_REGEX.findAll(list).map { it.groupValues[1] }.toList()
    }

    fun parseArticles(xml: String): List<PubMedArticle> =
        ARTICLE_REGEX.findAll(xml).mapNotNull { match ->
            val body = match.groupValues[1]
            val pmid = Regex("<PMID\\b[^>]*>(\\d+)</PMID>").find(body)?.groupValues?.get(1) ?: return@mapNotNull null
            val title = RssParser.cleanText(tag(body, "ArticleTitle") ?: "")
            val abstractParts = Regex("<AbstractText\\b([^>]*)>(.*?)</AbstractText>", RegexOption.DOT_MATCHES_ALL)
                .findAll(body)
                .map { part ->
                    val label = Regex("Label=\"([^\"]+)\"").find(part.groupValues[1])?.groupValues?.get(1)
                    val text = RssParser.cleanText(part.groupValues[2])
                    if (label != null) "$label: $text" else text
                }
                .toList()
            val journalBlock = tag(body, "Journal") ?: ""
            val journal = RssParser.cleanText(tag(journalBlock, "Title") ?: "")
            val pubDate = tag(body, "PubDate") ?: ""
            val year = tag(pubDate, "Year") ?: Regex("\\b(19|20)\\d{2}\\b").find(tag(pubDate, "MedlineDate") ?: "")?.value ?: ""
            PubMedArticle(pmid, title, abstractParts.joinToString(" "), journal, year)
        }.toList()

    private fun tag(body: String, name: String): String? =
        Regex("<$name\\b[^>]*>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)
}
