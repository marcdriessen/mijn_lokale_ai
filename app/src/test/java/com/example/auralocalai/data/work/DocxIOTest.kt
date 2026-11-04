package com.example.auralocalai.data.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxIOTest {

    @Test
    fun roundTripKeepsTextAndHeadings() {
        val body = "S – Subjectief:\nCliënt zegt: \"ik slaap slecht\" & is onrustig.\n\nO – Objectief:\nOnrustig\tgespannen."
        val bytes = DocxIO.write("Rapportage (SOEP)", "Geanonimiseerd", body)
        assertTrue(DocxIO.looksLikeDocx(bytes))
        val text = DocxIO.readText(bytes.inputStream())!!
        assertTrue(text.startsWith("Rapportage (SOEP)"))
        assertTrue(text.contains("Cliënt zegt: \"ik slaap slecht\" & is onrustig."))
        assertTrue(text.contains("Onrustig\tgespannen."))
    }

    @Test
    fun readsWordXmlParagraphsAndEntities() {
        val xml = "<w:document><w:body><w:p><w:pPr><w:pStyle w:val=\"Kop1\"/></w:pPr><w:r><w:t>Dag</w:t></w:r><w:r><w:t xml:space=\"preserve\"> rapport</w:t></w:r></w:p>" +
            "<w:p/><w:p><w:r><w:t>A &amp; B &lt;3</w:t><w:br/><w:t>regel 2</w:t></w:r></w:p><w:tbl><w:tr><w:tc><w:p><w:r><w:t>cel</w:t></w:r></w:p></w:tc></w:tr></w:tbl></w:body></w:document>"
        assertEquals("Dag rapport\n\nA & B <3\nregel 2\ncel", DocxIO.xmlToText(xml))
    }

    @Test
    fun detectsHeadings() {
        assertTrue(DocxIO.isHeading("S – Subjectief:"))
        assertTrue(DocxIO.isHeading("Plan"))
        assertTrue(DocxIO.isHeading("E (Evaluatie)"))
        assertTrue(DocxIO.isHeading("P:"))
        assertFalse(DocxIO.isHeading("Pas morgen opnieuw bespreken met de arts."))
        assertFalse(DocxIO.isHeading("Slaapt slecht"))
        assertEquals("Plan\n• afspraak", DocxIO.cleanMarkdown("## **Plan**\n- afspraak"))
    }
}
