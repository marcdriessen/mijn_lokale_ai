package com.example.auralocalai.data.work

import com.example.auralocalai.data.privacy.Anonymizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnonymizerTest {

    @Test
    fun removesDirectIdentifiers() {
        val input = "Dhr. Jan de Vries (BSN 111222333), geb. 12-03-1968, woont Kerkstraat 12a, 1234 AB Utrecht. " +
            "Tel 06-12345678, mail jan.devries@example.nl. Cliëntnummer: 4455667. IBAN NL91 ABNA 0417 1643 00. " +
            "Overleg met J. Jansen over de CIWA-score van 14."
        val result = Anonymizer.anonymize(input)
        val out = result.text
        assertFalse(out, out.contains("Vries"))
        assertFalse(out, out.contains("111222333"))
        assertFalse(out, out.contains("1968"))
        assertFalse(out, out.contains("Kerkstraat"))
        assertFalse(out, out.contains("1234 AB"))
        assertFalse(out, out.contains("12345678"))
        assertFalse(out, out.contains("example.nl"))
        assertFalse(out, out.contains("4455667"))
        assertFalse(out, out.contains("ABNA"))
        assertFalse(out, out.contains("Jansen"))
        assertTrue(out, out.contains("CIWA-score van 14"))
        assertTrue(out, out.contains("Dhr. [NAAM]"))
        assertTrue(result.replacements >= 9)
    }

    @Test
    fun bsnElevenCheck() {
        assertTrue(Anonymizer.isValidBsn("111222333"))
        assertFalse(Anonymizer.isValidBsn("123456789"))
        // A random 9-digit number that fails the 11-check stays.
        assertTrue(Anonymizer.anonymize("Ordernummer 123456789").text.contains("123456789"))
    }

    @Test
    fun masksExtraNamesFromModel() {
        val names = Anonymizer.parseNames("Petra, Ahmed El Amrani, NONE, [NAAM]")
        assertEquals(listOf("Petra", "Ahmed El Amrani"), names)
        val out = Anonymizer.anonymize("Petra zag Ahmed El Amrani op de gang. Petrapolis blijft.", names).text
        assertEquals("[NAAM] zag [NAAM] op de gang. Petrapolis blijft.", out)
    }
}
