package com.example.auralocalai.data.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateLanguagesTest {

    @Test
    fun offersRequestedLanguages() {
        listOf("nl", "en", "bg", "pl", "de").forEach { code ->
            assertTrue(code, TranslateLanguages.ALL.any { it.code == code })
        }
        assertEquals("Polish", TranslateLanguages.englishName("pl"))
    }

    @Test
    fun chunksKeepParagraphsAndRespectLimit() {
        val text = (1..30).joinToString("\n\n") { "Alinea $it met wat tekst om te vertalen." }
        val parts = TranslateLanguages.chunks(text, maxChars = 200)
        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.length <= 200 })
        assertEquals(text.replace("\n\n", " "), parts.joinToString(" ") { it.replace("\n\n", " ") })
    }

    @Test
    fun longParagraphIsSplitOnLines() {
        val para = (1..40).joinToString("\n") { "Regel $it van een lange lijst" }
        val parts = TranslateLanguages.chunks(para, maxChars = 150)
        assertTrue(parts.all { it.length <= 150 })
        assertEquals(para.replace("\n", " "), parts.joinToString(" ") { it.replace("\n", " ") })
        assertEquals(listOf("kort"), TranslateLanguages.chunks("kort"))
    }
}
