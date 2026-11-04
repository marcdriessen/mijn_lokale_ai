package com.example.auralocalai.data.medical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CrossrefParserTest {

    @Test
    fun parsesWork() {
        val body = """{"status":"ok","message":{"title":["Contingency management for <i>stimulant</i> use"],
            "container-title":["The Lancet Psychiatry"],"type":"journal-article",
            "issued":{"date-parts":[[2025,3,1]]}}}"""
        val s = CrossrefParser.parse(body, "10.1016/x")!!
        assertEquals("Contingency management for stimulant use", s.title)
        assertEquals("The Lancet Psychiatry", s.journal)
        assertEquals("2025", s.year)
        assertEquals("https://doi.org/10.1016/x", s.url)
        assertNull(CrossrefParser.parse("{\"message\":{}}", "x"))
    }
}
