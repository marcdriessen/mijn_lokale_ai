package com.example.auralocalai.data.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachReplyTest {

    @Test
    fun parsesAllParts() {
        val raw = "CORRECTION: Добър ден, аз се казвам Марк.\nEXPLANATION: 'ас' schrijf je als 'аз'.\nREPLY: Приятно ми е, Марк! Как си днес?\nTRANSLATION: Leuk je te ontmoeten, Mark! Hoe gaat het vandaag?"
        val r = CoachReply.parse(raw, "добре ден ас се казвам марк")
        assertEquals("Добър ден, аз се казвам Марк.", r.correction)
        assertTrue(r.explanation!!.contains("аз"))
        assertEquals("Приятно ми е, Марк! Как си днес?", r.reply)
        assertEquals("Leuk je te ontmoeten, Mark! Hoe gaat het vandaag?", r.translation)
    }

    @Test
    fun capitalsOnlyIsNotACorrection() {
        val raw = "CORRECTION: Много добре, благодаря.\nEXPLANATION: Hoofdletter.\nREPLY: Чудесно! Какво ще правиш днес?\nTRANSLATION: Geweldig! Wat ga je vandaag doen?"
        val r = CoachReply.parse(raw, "много добре, благодаря")
        assertNull(r.correction)
        assertNull(r.explanation)
        assertEquals("Чудесно! Какво ще правиш днес?", r.reply)
    }

    @Test
    fun okAndMarkdownAndOldLabels() {
        val r = CoachReply.parse("**CORRECTION:** OK\n**REPLY:** Здравей!\nКак си?", "Здравей")
        assertNull(r.correction)
        assertEquals("Здравей!\nКак си?", r.reply)
        val old = CoachReply.parse("Correctie: Аз съм добре.\nAntwoord: Супер!", "аз сум добре")
        assertEquals("Аз съм добре.", old.correction)
        assertEquals("Супер!", old.reply)
        assertEquals("Просто текст", CoachReply.parse("Просто текст", "x").reply)
    }
}
