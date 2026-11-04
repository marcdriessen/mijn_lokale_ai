package com.example.auralocalai.data

import com.example.auralocalai.data.providers.AiSearchParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSearchParserTest {

    @Test
    fun parsesGeminiGrounding() {
        val body = """{"candidates":[{"content":{"parts":[{"text":"Het kabinet trekt 10 miljoen uit. "},{"text":"Dat meldt NOS."}]},
          "groundingMetadata":{"webSearchQueries":["kabinet verslavingszorg"],
          "groundingChunks":[{"web":{"uri":"https://vertexaisearch.cloud.google.com/a","title":"nos.nl"}},{"web":{"uri":"https://vertexaisearch.cloud.google.com/b","title":"nu.nl"}}],
          "groundingSupports":[{"segment":{"text":"Het kabinet trekt 10 miljoen uit."},"groundingChunkIndices":[0,1]}]}}]}"""
        val sources = AiSearchParser.parseGemini(body, 5)
        assertEquals(3, sources.size)
        assertTrue(sources[0].snippet.startsWith("Het kabinet trekt 10 miljoen uit."))
        assertEquals("nos.nl", sources[1].domain)
        assertEquals("Het kabinet trekt 10 miljoen uit.", sources[1].snippet)
    }

    @Test
    fun parsesOpenAiCitations() {
        val text = "Intro zin. Het kabinet trekt 10 miljoen uit (nos.nl)."
        val start = text.indexOf("(nos.nl)")
        val body = """{"output":[{"type":"web_search_call","status":"completed"},{"type":"message","content":[{"type":"output_text","text":"$text",
          "annotations":[{"type":"url_citation","url":"https://nos.nl/artikel/1?utm_source=openai","title":"NOS","start_index":$start,"end_index":${text.length}}]}]}]}"""
        val sources = AiSearchParser.parseOpenAi(body, 5)
        assertEquals(2, sources.size)
        assertEquals("https://nos.nl/artikel/1", sources[1].url)
        assertTrue(sources[1].snippet.contains("Het kabinet trekt 10 miljoen uit"))
    }
}
