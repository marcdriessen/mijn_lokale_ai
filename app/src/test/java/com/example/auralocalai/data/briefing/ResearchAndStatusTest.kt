package com.example.auralocalai.data.briefing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchAndStatusTest {

    @Test
    fun researchQueryUsesEnglishKeywords() {
        val topic = BriefingTopic("t", "Verslavingszorg", listOf("verslaving", "addiction", "substance use", "зависимост", "AI"))
        val kws = ResearchFeed.englishKeywords(topic)
        assertTrue(kws.contains("addiction"))
        assertTrue(kws.none { k -> k.any { it.code > 127 } })
        assertTrue(kws.none { it == "AI" })
        assertEquals("(\"addiction\") AND FIRST_PDATE:[2026-10-01 TO 2026-11-01]", ResearchFeed.query(listOf("addiction"), "2026-10-01", "2026-11-01"))
    }

    @Test
    fun sourceStatusTracksLastSuccess() {
        val ok = SourceStatus.update(null, 100, 12, null)
        assertEquals(100, ok.lastSuccess)
        val failed = SourceStatus.update(ok, 200, 0, "HTTP 403")
        assertEquals(100, failed.lastSuccess)
        assertEquals("HTTP 403", failed.error)
        assertEquals("no items", SourceStatus.update(null, 300, 0, null).error)
    }
}
