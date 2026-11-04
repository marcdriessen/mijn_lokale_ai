package com.example.auralocalai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.auralocalai.data.ModelRouting.Task as T

class ModelRoutingTest {

    @Test
    fun picksSpecialistWhenDownloaded() {
        val ids = setOf("gemma4-e4b", "hy-mt2-1.8b", "medgemma-1.5-4b")
        assertEquals("hy-mt2-1.8b", ModelRouting.choose(T.TRANSLATE, ids, ModelRouting.AUTO, true))
        assertEquals("medgemma-1.5-4b", ModelRouting.choose(T.MEDICAL, ids, null, true))
        assertEquals("gemma4-e4b", ModelRouting.choose(T.CHAT, ids, ModelRouting.AUTO, true))
        assertEquals("gemma4-e4b", ModelRouting.choose(T.COACH, ids, ModelRouting.AUTO, true))
    }

    @Test
    fun fallsBackToGeneralModel() {
        val ids = setOf("gemma4-e2b")
        assertEquals("gemma4-e2b", ModelRouting.choose(T.TRANSLATE, ids, ModelRouting.AUTO, true))
        assertEquals("gemma4-e2b", ModelRouting.choose(T.MEDICAL, ids, ModelRouting.AUTO, true))
    }

    @Test
    fun translationModelNeverUsedForOtherTasks() {
        val ids = setOf("hy-mt2-1.8b")
        ModelRouting.Task.entries.filter { it != T.TRANSLATE }.forEach {
            assertNull(ModelRouting.choose(it, ids, ModelRouting.AUTO, true))
        }
    }

    @Test
    fun disabledOrCurrentKeepsLoadedModel() {
        val ids = setOf("gemma4-e4b", "hy-mt2-1.8b")
        assertNull(ModelRouting.choose(T.TRANSLATE, ids, ModelRouting.AUTO, false))
        assertNull(ModelRouting.choose(T.TRANSLATE, ids, ModelRouting.CURRENT, true))
    }

    @Test
    fun manualChoiceUsedWhenDownloaded() {
        val ids = setOf("gemma4-e4b", "gemma4-12b")
        assertEquals("gemma4-12b", ModelRouting.choose(T.MEDICAL, ids, "gemma4-12b", true))
        // Not downloaded: automatic choice instead.
        assertEquals("gemma4-e4b", ModelRouting.choose(T.MEDICAL, ids, "medgemma-1.5-4b", true))
    }

    @Test
    fun allPreferredIdsArePresets() {
        val known = ModelPreset.presets.map { it.id }.toSet()
        ModelRouting.PREFERENCES.values.flatten().forEach { assertTrue(it in known) }
        assertEquals(ModelRouting.Task.entries.toSet(), ModelRouting.PREFERENCES.keys)
    }

    @Test
    fun downloadedIdsFromFileNames() {
        val ids = ModelRouting.downloadedIds(listOf("gemma4-e4b.litertlm", "hy-mt2-1.8b.litertlm", "eigen-model.litertlm"))
        assertEquals(setOf("gemma4-e4b", "hy-mt2-1.8b"), ids)
    }
}
