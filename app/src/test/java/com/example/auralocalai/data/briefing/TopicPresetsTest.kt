package com.example.auralocalai.data.briefing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicPresetsTest {

    @Test
    fun applyKeepsAddsAndDisables() {
        val current = listOf(BriefingTopic("care_ggz", "Ggz", listOf("ggz")), BriefingTopic("preset_sport", "Sport", listOf("eigen")))
        val result = TopicPresets.apply(current, setOf("preset_sport", "preset_tech"), "nl")
        assertEquals(3, result.size)
        assertFalse(result.first { it.id == "care_ggz" }.enabled)
        assertEquals(listOf("eigen"), result.first { it.id == "preset_sport" }.keywords)
        val tech = result.first { it.id == "preset_tech" }
        assertEquals("Technologie en Wetenschap", tech.name)
        assertTrue(tech.keywords.contains("наука"))
    }
}
