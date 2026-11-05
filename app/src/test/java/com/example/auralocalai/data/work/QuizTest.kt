package com.example.auralocalai.data.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizTest {

    private fun cards(n: Int) = (1..n).map { Flashcard("Vraag $it?", "Antwoord nummer $it.") }

    @Test
    fun buildsQuestionsWithFourDistinctOptionsAndTheRightAnswer() {
        val deck = cards(8)
        val questions = Quiz.build(deck, count = 5, random = Random(1))
        assertEquals(5, questions.size)
        questions.forEach { q ->
            assertEquals(Quiz.OPTIONS, q.options.size)
            assertEquals(Quiz.OPTIONS, q.options.distinct().size)
            assertEquals(deck[q.cardIndex].question, q.question)
            assertEquals(Quiz.shorten(deck[q.cardIndex].answer), q.options[q.correct])
        }
        assertEquals(5, questions.map { it.cardIndex }.distinct().size)
    }

    @Test
    fun needsAtLeastFourDifferentAnswers() {
        assertTrue(Quiz.build(cards(3), count = 5).isEmpty())
        val same = (1..6).map { Flashcard("V$it", "Zelfde antwoord.") }
        assertTrue(Quiz.build(same, count = 5).isEmpty())
    }

    @Test
    fun lessKnownCardsComeFirst() {
        val deck = cards(10).mapIndexed { i, c -> if (i == 7) c.copy(box = 1) else c.copy(box = 4) }
        val first = Quiz.build(deck, count = 1, random = Random(3)).single()
        assertEquals(7, first.cardIndex)
    }

    @Test
    fun shortenKeepsWholeSentences() {
        val long = "Eerste zin is kort. " + "Tweede zin is veel langer dan de rest van de tekst. ".repeat(10)
        val short = Quiz.shorten(long, max = 80)
        assertTrue(short.length <= 80)
        assertTrue(short.startsWith("Eerste zin is kort."))
        assertEquals("Korte tekst.", Quiz.shorten("  Korte\n tekst. "))
    }
}
