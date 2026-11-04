package com.example.auralocalai.data.medical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EuropePmcParserTest {

    @Test
    fun parsesCoreResult() {
        val json = """{"hitCount":1,"resultList":{"result":[{"id":"123","source":"MED","pmid":"123","doi":"10.1/x",
            "title":"Relapse prevention in <i>alcohol</i> use disorder.","abstractText":"Background: ...",
            "journalInfo":{"journal":{"title":"Addiction"}},"pubYear":"2025","isOpenAccess":"Y",
            "pubTypeList":{"pubType":["Systematic Review"]},
            "fullTextUrlList":{"fullTextUrl":[{"url":"https://europepmc.org/articles/PMC1"}]}}]}}"""
        val a = EuropePmcParser.parse(json).single()
        assertEquals("Relapse prevention in alcohol use disorder.", a.title)
        assertEquals("Addiction", a.journal)
        assertEquals("2025", a.year)
        assertTrue(a.isOpenAccess)
        assertEquals(listOf("Systematic Review"), a.publicationTypes)
        assertEquals("https://europepmc.org/article/MED/123", a.url)
        assertEquals(EuropePmcParser.titleKey("Relapse prevention in alcohol use disorder"), EuropePmcParser.titleKey(a.title))
        assertTrue(EuropePmcParser.parse("{}").isEmpty())
    }
}
