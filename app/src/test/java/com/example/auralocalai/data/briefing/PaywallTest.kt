package com.example.auralocalai.data.briefing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaywallTest {

    @Test
    fun recognisesPaywalledSites() {
        assertTrue(Paywall.isPaywalled(NewsItem("t", "https://www.nrc.nl/nieuws/2026/x", "NRC")))
        assertTrue(Paywall.isPaywalled(NewsItem("t", "https://edition.ft.com/content/1", "FT")))
        assertFalse(Paywall.isPaywalled(NewsItem("t", "https://nos.nl/artikel/1", "NOS")))
        assertFalse(Paywall.isPaywalled(NewsItem("t", "https://www.thead.nl/x", "x")))
        assertEquals("volkskrant.nl", Paywall.domainOf("https://www.volkskrant.nl:443/a?b"))
    }

    @Test
    fun googleNewsPublisherIsRead() {
        val xml = "<rss><channel><item><title>Kabinet besluit iets - de Volkskrant</title>" +
            "<link>https://news.google.com/rss/articles/abc</link>" +
            "<source url=\"https://www.volkskrant.nl\">de Volkskrant</source></item>" +
            "<item><title>Nieuws - NOS</title><link>https://news.google.com/rss/articles/def</link>" +
            "<source url=\"https://nos.nl\">NOS</source></item></channel></rss>"
        val items = RssParser.parse(xml, "Google News")
        assertEquals("volkskrant.nl", items[0].publisherDomain)
        assertTrue(Paywall.isPaywalled(items[0]))
        assertFalse(Paywall.isPaywalled(items[1]))
    }

    @Test
    fun migrationAddsFreeSourcesOnce() {
        val old = BriefingConfig(sources = DefaultBriefing.sources, profiles = listOf(DefaultBriefing.careProfile))
        val migrated = DefaultBriefing.migrate(old)
        assertTrue(migrated.sources.any { it.id == "bbc_world" })
        assertEquals(DefaultBriefing.SOURCES_VERSION, migrated.sourcesVersion)
        val removed = migrated.copy(sources = migrated.sources.filterNot { it.id == "bbc_world" })
        assertFalse(DefaultBriefing.migrate(removed).sources.any { it.id == "bbc_world" })
    }

    @Test
    fun dnevnikIsPaywalledAndTurnedOff() {
        assertTrue(Paywall.isPaywalled(NewsItem("t", "https://www.dnevnik.bg/bulgaria/2026/11/04/1_x/", "Дневник")))
        val v6 = BriefingConfig(
            sources = listOf(NewsSource("dnevnik", "Дневник", "https://www.dnevnik.bg/rss/", enabled = true)),
            profiles = listOf(DefaultBriefing.careProfile),
            sourcesVersion = 6
        )
        val migrated = DefaultBriefing.migrate(v6)
        assertFalse(migrated.sources.first { it.id == "dnevnik" }.enabled)
        assertFalse(DefaultBriefing.config.sources.first { it.id == "dnevnik" }.enabled)
        // A source switched on again by the user stays on.
        val again = migrated.copy(sources = migrated.sources.map { if (it.id == "dnevnik") it.copy(enabled = true) else it })
        assertTrue(DefaultBriefing.migrate(again).sources.first { it.id == "dnevnik" }.enabled)
    }

    @Test
    fun bulgarianPaywallTextIsRecognised() {
        val html = "<html><body><p>Кратко въведение.</p><div>Тази статия е достъпна само за абонати. Стани абонат</div></body></html>"
        assertEquals(ArticleContent.Access.PAYWALL, ArticleContent.access(html, "Кратко въведение."))
    }
}
