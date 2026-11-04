package com.example.auralocalai.data.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTextExtractorTest {

    @Test
    fun extractsTitleAndParagraphsWithoutScriptsOrMenus() {
        val html = """<html><head><title>Radkasten &amp; Aufbau</title><script>var x = "<p>nope</p>";</script></head>
            <body><nav><p>Menu item that should not appear in the text at all</p></nav>
            <h1>Technische Daten des Fahrzeugs und der Ausstattung</h1>
            <p>Das zulässige Gesamtgewicht beträgt 3.500 kg, die Standheizung ist serienmäßig.</p>
            <p>kurz</p></body></html>"""
        val page = PageTextExtractor.extract(html)
        assertEquals("Radkasten & Aufbau", page.title)
        assertTrue(page.text.contains("zulässige Gesamtgewicht"))
        assertTrue(!page.text.contains("Menu item"))
        assertTrue(!page.text.contains("nope"))
        assertTrue(!page.text.contains("kurz"))
    }

    @Test
    fun findsFirstUrlInSharedText() {
        assertEquals("https://example.com/a?b=1", PageTextExtractor.firstUrl("Lees dit: https://example.com/a?b=1."))
        assertEquals(null, PageTextExtractor.firstUrl("geen link"))
    }
}
