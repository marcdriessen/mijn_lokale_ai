package com.example.auralocalai.data.briefing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class GoogleNewsResolverTest {

    @Test
    fun oldFormatIdContainsUrl() {
        val raw = byteArrayOf(0x08, 0x13, 0x22, 0x2B) + "https://nos.nl/artikel/2541234-test".toByteArray() + byteArrayOf(0xD2.toByte(), 0x01, 0x00)
        val id = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        val link = "https://news.google.com/rss/articles/$id?oc=5"
        assertTrue(GoogleNewsResolver.isGoogleNews(link))
        assertEquals(id, GoogleNewsResolver.articleId(link))
        assertEquals("https://nos.nl/artikel/2541234-test", GoogleNewsResolver.decodeOldFormat(id))
    }

    @Test
    fun newFormatPartsAreParsed() {
        assertNull(GoogleNewsResolver.decodeOldFormat("AU_yqLNoUrlInside"))
        val html = """<c-wiz><div jscontroller="x" data-n-a-sg="AZ5r3eXyz" data-n-a-ts="1730700000"></div>"""
        assertEquals("AZ5r3eXyz" to "1730700000", GoogleNewsResolver.parseSignature(html))
        val payload = GoogleNewsResolver.batchPayload("ID1", "1730700000", "SIG")
        assertTrue(payload.startsWith("[[[\"Fbv4je\""))
        assertTrue(payload.contains("\\\"ID1\\\",1730700000,\\\"SIG\\\""))
        val response = ")]}'\n\n[[\"wrb.fr\",\"Fbv4je\",\"[\\\"garturlres\\\",\\\"https://www.ggznieuws.nl/artikel-1/\\\",1]\",null,null,null,\"generic\"]]"
        assertEquals("https://www.ggznieuws.nl/artikel-1/", GoogleNewsResolver.parseBatchResponse(response))
    }
}
