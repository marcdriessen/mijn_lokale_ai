package com.example.auralocalai.data.briefing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleContentTest {

    private val para = "Dit is een normale alinea uit een nieuwsartikel over de verslavingszorg in Nederland en de gevolgen."

    @Test
    fun usableTextNeedsRealParagraphs() {
        assertTrue(ArticleContent.isUsable(List(6) { para }.joinToString("\n\n")))
        assertFalse(ArticleContent.isUsable(para))
        val cookieWall = List(4) { "Wij gebruiken cookies om je ervaring te verbeteren. Accepteer alle cookies of pas je privacy aan." }
            .joinToString("\n\n") + "\n\n" + para
        assertFalse(ArticleContent.isUsable(cookieWall))
    }

    @Test
    fun detectsAnnouncedFeeds() {
        val html = """<html><head><link rel="stylesheet" href="/a.css">
            <link rel="alternate" type="application/rss+xml" title="Nieuws" href="/feed/">
            <link type="application/atom+xml" rel="alternate" href="https://example.org/atom.xml"></head></html>"""
        assertEquals(listOf("https://www.example.nl/feed/", "https://example.org/atom.xml"), ArticleContent.detectFeeds(html, "https://www.example.nl/nieuws/"))
        assertTrue(ArticleContent.looksLikeFeed("<?xml version=\"1.0\"?><rss version=\"2.0\">"))
        assertFalse(ArticleContent.looksLikeFeed("<!doctype html><html>"))
    }

    @Test
    fun feedFullTextIsRead() {
        val xml = "<rss><channel><item><title>Kop</title><link>https://x.nl/a</link><description>Kort</description>" +
            "<content:encoded><![CDATA[<p>Eerste alinea.</p><p>Tweede &amp; laatste alinea.</p>]]></content:encoded></item></channel></rss>"
        val item = RssParser.parse(xml, "X").single()
        assertEquals("Eerste alinea.\n\nTweede & laatste alinea.", item.content)
        assertEquals("Kort", item.description)
    }

    @Test
    fun promptUsesFullTextWhenAvailable() {
        val item = NewsItem("Kop", "https://x.nl", "X", description = "Intro")
        assertTrue(BriefingPrompts.articlePrompt(item, "Ggz", "Dutch", "HELE TEKST").contains("HELE TEKST"))
        assertTrue(BriefingPrompts.articlePrompt(item, "Ggz", "Dutch").contains("only the introduction"))
    }

    @Test
    fun recognisesPaywallAndBlocks() {
        val full = List(8) { para }.joinToString("\n\n")
        assertEquals(ArticleContent.Access.FREE, ArticleContent.access("<html><footer>Word abonnee</footer></html>", full))
        assertEquals(ArticleContent.Access.PAYWALL, ArticleContent.access("<div>Само за абонати</div>", para))
        assertEquals(ArticleContent.Access.PAYWALL, ArticleContent.access("<script type=\"application/ld+json\">{\"isAccessibleForFree\": false}</script>", para))
        assertEquals(ArticleContent.Access.BLOCKED, ArticleContent.access("<title>Attention Required! | Cloudflare</title> Sorry, you have been blocked", ""))
        assertEquals(ArticleContent.Access.UNREADABLE, ArticleContent.access("<html></html>", "kort"))
    }
}
