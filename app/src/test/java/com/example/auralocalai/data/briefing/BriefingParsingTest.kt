package com.example.auralocalai.data.briefing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BriefingParsingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val rss = """<?xml version="1.0"?><rss><channel><title>X</title>
        <item><title><![CDATA[Verstappen wins Formula 1 race &amp; more]]></title><link>https://ex.com/a</link>
        <description>&lt;p&gt;Max &lt;b&gt;won&lt;/b&gt;&lt;/p&gt;</description><pubDate>Tue, 03 Nov 2026 10:00:00 GMT</pubDate></item>
        <item><title>AI chip news</title><link>https://ex.com/b</link><pubDate>Wed, 04 Nov 2026 08:00:00 +0000</pubDate></item>
        </channel></rss>"""

    @Test
    fun parsesRss2WithCdataEntitiesAndDates() {
        val items = RssParser.parse(rss, "Test")
        assertEquals(2, items.size)
        assertEquals("Verstappen wins Formula 1 race & more", items[0].title)
        assertEquals("Max won", items[0].description)
        assertEquals("https://ex.com/a", items[0].link)
        assertNotNull(items[0].publishedMillis)
        assertNotNull(items[1].publishedMillis)
    }

    @Test
    fun parsesAtomAndRdf() {
        val atom = """<feed><entry><title type="html">Bulgaria election</title><link rel="alternate" href="https://ex.com/c"/>
            <updated>2026-11-04T07:00:00Z</updated><summary>Sofia vote</summary></entry></feed>"""
        val atomItems = RssParser.parse(atom, "Atom")
        assertEquals(1, atomItems.size)
        assertEquals("https://ex.com/c", atomItems[0].link)
        assertNotNull(atomItems[0].publishedMillis)

        val rdf = """<rdf:RDF><channel><items><rdf:Seq/></items></channel><item rdf:about="x"><title>DW story</title>
            <link>https://dw.com/x</link><dc:date>2026-11-04T06:00:00+01:00</dc:date></item></rdf:RDF>"""
        val rdfItems = RssParser.parse(rdf, "DW")
        assertEquals(1, rdfItems.size)
        assertNotNull(rdfItems[0].publishedMillis)
    }

    @Test
    fun matchesKeywordsInAnyLanguage() {
        val items = RssParser.parse(rss, "Test")
        val f1 = BriefingTopic("f1", "Formule 1", listOf("Formula 1", "F1", "Verstappen"))
        assertEquals(1, TopicMatcher.selectForTopic(items, f1, 5).size)
        assertEquals(1, TopicMatcher.selectForTopic(items, BriefingTopic("ai", "AI"), 5).size)
        assertFalse(TopicMatcher.matches(NewsItem("Said the chairman", "l", "s"), listOf("AI")))
        assertTrue(TopicMatcher.matches(NewsItem("Избори в България", "l", "s"), listOf("България")))
    }

    @Test
    fun removesDuplicateHeadlinesAndSortsNewestFirst() {
        val items = listOf(
            NewsItem("EU summit ends", "a", "DW", 1L),
            NewsItem("EU summit ends!", "b", "Reuters", 3L),
            NewsItem("EU budget agreed", "c", "AP", 2L)
        )
        val selected = TopicMatcher.selectForTopic(items, BriefingTopic("eu", "EU"), 5)
        assertEquals(listOf("EU budget agreed", "EU summit ends"), selected.map { it.title }.sorted())
        assertEquals("EU budget agreed", selected.first().title)
    }

    @Test
    fun parsesKeywordLists() {
        assertEquals(
            listOf("Formule 1", "Formula 1", "F1", "Verstappen"),
            TopicMatcher.parseKeywordList("Formule 1, Formula 1; F1\n- Verstappen, formula 1")
        )
    }

    @Test
    fun promptStaysWithinBudget() {
        val items = List(30) { NewsItem("Title $it", "l", "Src", 1_700_000_000_000L, "desc ".repeat(60)) }
        val prompt = BriefingPrompts.topicPrompt("F1", items, "Dutch", 1_500)
        assertTrue("prompt length ${prompt.length}", prompt.length < 1_700)
        assertTrue(prompt.contains("Title 0"))
    }

    @Test
    fun repositoryRoundTrip() {
        val repo = BriefingRepository(tempFolder.root)
        assertEquals(DefaultBriefing.topics.size, repo.loadConfig().topics.size)
        repo.saveConfig(repo.loadConfig().copy(autoUpdateEnabled = true, autoUpdateHour = 7))
        assertEquals(7, repo.loadConfig().autoUpdateHour)
        val briefing = Briefing("1", 1L, listOf(BriefingSection("t", "T", "sum", listOf(NewsItem("a", "l", "s", 5L)))), "nl")
        repo.saveBriefing(briefing)
        assertEquals("a", repo.loadHistory().single().sections.single().items.single().title)
        assertTrue(repo.deleteBriefing("1").isEmpty())
    }
}
