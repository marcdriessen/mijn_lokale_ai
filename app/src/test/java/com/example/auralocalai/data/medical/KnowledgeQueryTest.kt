package com.example.auralocalai.data.medical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeQueryTest {

    @Test
    fun normalizeMatchesBuildScript() {
        // Same rules as normaliseer() in tools/maak_kennisbank.py
        assertEquals("opioide verslaving en cocaine", KnowledgeQuery.normalize("Opioïde-verslaving en cocaïne!"))
        assertEquals("delirium tremens dts 2", KnowledgeQuery.normalize("  Delirium tremens (DTs), 2 "))
    }

    @Test
    fun termsDropStopwordsAndShortWords() {
        val terms = KnowledgeQuery.terms("Wat is de behandeling van alcoholontwenning bij een patiënt?")
        assertTrue("behandeling" in terms)
        assertTrue(terms.any { it.startsWith("alcoholontwenning") })
        assertFalse("wat" in terms)
        assertFalse("patient" in terms)
        assertFalse("de" in terms)
    }

    @Test
    fun stemShortensDutchPlurals() {
        assertEquals("verslaafd", KnowledgeQuery.stem("verslaafden"))
        assertEquals("benzodiazepin", KnowledgeQuery.stem("benzodiazepines"))
        assertEquals("roken", KnowledgeQuery.stem("roken"))
    }

    @Test
    fun compoundSplitsIncludeDutchParts() {
        val splits = KnowledgeQuery.compoundSplits("alcoholonthouding")
        assertTrue("alcohol" to "onthouding" in splits)
        assertTrue("opiaat" to "verslaving" in KnowledgeQuery.compoundSplits("opiaatsverslaving"))
        assertTrue(KnowledgeQuery.compoundSplits("delier").isEmpty())
    }

    @Test
    fun matchExpressionsUsePrefixes() {
        assertEquals("methadon* onderhoud*", KnowledgeQuery.matchAll(listOf("methadon", "onderhoud")))
        assertEquals("methadon* OR onderhoud*", KnowledgeQuery.matchAny(listOf("methadon", "onderhoud")))
    }

    @Test
    fun scorePrefersEntryCoveringRareTerms() {
        val terms = listOf("methadon", "dosering")
        val df = mapOf("methadon" to 50, "dosering" to 5_000)
        val relevant = KnowledgeQuery.score(terms, "Hoe werkt methadon?", "Hoe werkt methadon? Methadon is een opioïde agonist.", df, 300_000)
        val generic = KnowledgeQuery.score(terms, "Wat is een dosering?", "Wat is een dosering? Een dosering is de hoeveelheid.", df, 300_000)
        val both = KnowledgeQuery.score(terms, "Methadon dosering?", "Methadon dosering bij onderhoud.", df, 300_000)
        assertTrue(relevant > generic)
        assertTrue(both > relevant)
        assertEquals(0.0, KnowledgeQuery.score(terms, "Iets anders", "Iets heel anders.", df, 300_000), 0.0)
    }
}
