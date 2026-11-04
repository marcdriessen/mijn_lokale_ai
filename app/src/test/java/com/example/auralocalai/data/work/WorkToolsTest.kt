package com.example.auralocalai.data.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkToolsTest {

    @Test
    fun scoresAndBands() {
        assertEquals("moderate", ScaleScoring.band(ScaleScoring.total(listOf(3, 3, 2, 2, 0, 0, 0, 0, 1, 1)), ScaleScoring.CIWA_BANDS)?.key)
        assertEquals("severe", ScaleScoring.band(40, ScaleScoring.CIWA_BANDS)?.key)
        assertEquals("hazardous", ScaleScoring.band(8, ScaleScoring.AUDIT_BANDS)?.key)
        assertEquals("moderately_severe", ScaleScoring.band(30, ScaleScoring.COWS_BANDS)?.key)
    }

    @Test
    fun parsesFlashcardsInSeveralFormats() {
        val raw = """
            Q: Wat meet de CIWA-Ar? | A: Ernst van alcoholonthouding
            - V: Hoeveel items heeft de AUDIT?
              A: Tien
            Vraag 3: Wat is craving? | Antwoord: Sterk verlangen naar een middel
            Losse regel zonder label
        """.trimIndent()
        val cards = Flashcards.parse(raw)
        assertEquals(3, cards.size)
        assertEquals("Wat meet de CIWA-Ar?", cards[0].question)
        assertEquals("Tien", cards[1].answer)
        assertEquals("Sterk verlangen naar een middel", cards[2].answer)
    }

    @Test
    fun leitnerReview() {
        val card = Flashcard("q", "a")
        val known = Flashcards.review(card, known = true, now = 0L)
        assertEquals(2, known.box)
        assertTrue(known.dueAt > 0L)
        val forgotten = Flashcards.review(known, known = false, now = 0L)
        assertEquals(1, forgotten.box)
        assertEquals(0L, forgotten.dueAt)
    }
}
