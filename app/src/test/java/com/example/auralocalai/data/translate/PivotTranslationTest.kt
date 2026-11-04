package com.example.auralocalai.data.translate

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PivotTranslationTest {

    @Test
    fun keepsStructureAndCode() = runBlocking {
        val english = "## Tips\n- **Sleep** well\n1. Drink water\n\n```python\nprint('hi')\n```\n42"
        val dictionary = mapOf("Tips" to "Tips", "Sleep well" to "Slaap goed", "Drink water" to "Drink water")
        val result = PivotTranslation.translateMarkdown(english) { dictionary[it] ?: "NL($it)" }
        assertEquals("## Tips\n- Slaap goed\n1. Drink water\n\n```python\nprint('hi')\n```\n42", result)
    }

    @Test
    fun failedLineKeepsOriginal() = runBlocking {
        assertEquals("Hello", PivotTranslation.translateMarkdown("Hello") { null })
    }
}
