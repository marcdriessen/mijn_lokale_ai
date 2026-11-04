package com.example.auralocalai.data.briefing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsIntelligenceTest {

    @Test
    fun scoresAreParsedAndRanked() {
        val scores = NewsIntelligence.parseScores("1: 3\n2. 9/10\n3 - 7\nfoo\n9: 10", 3)
        assertEquals(mapOf(0 to 3, 1 to 9, 2 to 7), scores)
        assertEquals(listOf("b", "c", "a"), NewsIntelligence.rankByScores(listOf("a", "b", "c"), scores))
        assertEquals(listOf("a", "b"), NewsIntelligence.rankByScores(listOf("a", "b"), emptyMap()))
    }

    @Test
    fun groupsReportsAboutSameEvent() {
        val chosen = NewsItem("Kabinet trekt extra geld uit voor verslavingszorg - NOS", "https://nos.nl/1", "NOS")
        val pool = listOf(
            NewsItem("Extra geld kabinet voor verslavingszorg", "https://nu.nl/2", "NU.nl"),
            NewsItem("Kabinet trekt extra geld uit voor verslavingszorg", "https://nos.nl/3", "NOS"),
            NewsItem("Voetbal: Ajax wint van PSV", "https://nu.nl/4", "NU.nl")
        )
        val related = NewsIntelligence.relatedReports(chosen, pool)
        assertEquals(listOf("https://nu.nl/2"), related.map { it.link })
        assertTrue(NewsIntelligence.comparePrompt("Dutch", listOf(chosen) + related).contains("[2] NU.nl"))
    }

    @Test
    fun findsDoi() {
        assertEquals("10.1016/S0140-6736(25)00001-2".substringBefore("("), NewsIntelligence.findDoi("zie doi.org/10.1016/S0140-6736(25)00001-2"))
        assertEquals("10.1001/jama.2025.1234", NewsIntelligence.findDoi("geen", "Published in JAMA, https://doi.org/10.1001/jama.2025.1234."))
        assertNull(NewsIntelligence.findDoi("geen doi hier"))
        assertTrue(NewsIntelligence.mentionsResearch("Nieuw onderzoek toont aan"))
    }
}
