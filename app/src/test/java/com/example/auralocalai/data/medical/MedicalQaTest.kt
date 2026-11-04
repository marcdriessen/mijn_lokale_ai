package com.example.auralocalai.data.medical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MedicalQaTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun parsesPubMedIdsAndArticles() {
        val json = """{"header":{},"esearchresult":{"count":"2","idlist":["38000001","37000002"]}}"""
        assertEquals(listOf("38000001", "37000002"), PubMedParser.parseIdList(json))

        val xml = """<PubmedArticleSet><PubmedArticle><MedlineCitation><PMID Version="1">38000001</PMID>
            <Article><Journal><JournalIssue><PubDate><Year>2024</Year></PubDate></JournalIssue><Title>Addiction</Title></Journal>
            <ArticleTitle>Benzodiazepines for alcohol withdrawal &amp; delirium</ArticleTitle>
            <Abstract><AbstractText Label="BACKGROUND">Alcohol withdrawal is common.</AbstractText>
            <AbstractText Label="RESULTS">Symptom-triggered dosing reduced use.</AbstractText></Abstract></Article>
            </MedlineCitation></PubmedArticle></PubmedArticleSet>"""
        val article = PubMedParser.parseArticles(xml).single()
        assertEquals("38000001", article.pmid)
        assertEquals("Benzodiazepines for alcohol withdrawal & delirium", article.title)
        assertEquals("Addiction", article.journal)
        assertEquals("2024", article.year)
        assertTrue(article.abstract.startsWith("BACKGROUND: Alcohol withdrawal is common."))
        assertEquals("https://pubmed.ncbi.nlm.nih.gov/38000001/", article.url)
    }

    @Test
    fun libraryFindsRelevantPassage() {
        val text = "Protocol alcoholonthouding.\n\nBij alcoholonthouding wordt de CIWA-Ar score elke 4 uur gemeten.\n\n" +
            "Valpreventie op de afdeling: controleer schoeisel en verlichting."
        val document = LibraryDocument("1", "Protocol", 0L, PassageIndex.chunk(text, targetChars = 80))
        val hits = PassageIndex.search(listOf(document), "Hoe vaak meet je de CIWA bij alcoholonthouding?")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.first().passage.contains("CIWA"))
        assertTrue(PassageIndex.search(listOf(document), "de het een").isEmpty())
    }

    @Test
    fun chunkingKeepsPassagesSmall() {
        val text = (1..60).joinToString(" ") { "Zin nummer $it over een richtlijn." }
        val passages = PassageIndex.chunk(text, targetChars = 200)
        assertTrue(passages.size > 3)
        assertTrue(passages.all { it.length <= 260 })
    }

    @Test
    fun promptsCiteAndStayWithinBudget() {
        val sources = List(10) { MedicalSource("WEB", "Bron $it", "https://x/$it", "tekst ".repeat(300), "GGZ Standaarden") }
        val prompt = MedicalPrompts.answerPrompt("Wat is de behandeling?", sources, "Dutch", 3_000)
        assertTrue("length ${prompt.length}", prompt.length < 3_400)
        assertTrue(prompt.contains("[1] Bron 0"))
        assertTrue(MedicalPrompts.systemPrompt("Dutch").contains("Never state medication doses"))
        assertEquals("delirium AND alcohol", MedicalPrompts.cleanQuery("Query: \"delirium AND alcohol\"\nextra"))
        assertEquals("vraag (site:a.nl OR site:b.nl)", MedicalPrompts.siteQuery("vraag", listOf("a.nl", "b.nl")))
    }

    @Test
    fun braveUsageIsCountedPerMonth() {
        val repo = MedicalRepository(tempFolder.root)
        val november = 1_793_750_400_000L // 2026-11-04
        assertEquals(0, repo.braveUsageThisMonth(november))
        repo.recordBraveUse(november)
        repo.recordBraveUse(november)
        assertEquals(2, repo.braveUsageThisMonth(november))
        val december = november + 40L * 24 * 60 * 60 * 1000
        assertEquals(0, repo.braveUsageThisMonth(december))
    }
}
