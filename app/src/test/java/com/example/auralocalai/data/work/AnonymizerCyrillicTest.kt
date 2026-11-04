package com.example.auralocalai.data.work

import com.example.auralocalai.data.privacy.Anonymizer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnonymizerCyrillicTest {

    @Test
    fun removesBulgarianIdentifiers() {
        val input = "Пациентът Иван Петров Георгиев, ЕГН 7501020018, роден на 2 януари 1975 г., живее на ул. Васил Левски 12, бл. 5, ап. 14. " +
            "Тел. +359 888 123 456. Разговор с г-жа Мария Иванова и д-р Н. Стоянов. Лична карта № 645123987. CIWA резултат 14."
        val result = Anonymizer.anonymize(input)
        val out = result.text
        listOf("Иван", "Петров", "Георгиев", "7501020018", "1975", "Левски", "бл. 5", "ап. 14", "888 123", "Мария", "Иванова", "Стоянов", "645123987")
            .forEach { assertFalse("$it in: $out", out.contains(it)) }
        assertTrue(out, out.contains("CIWA резултат 14"))
        assertTrue(out, out.contains("г-жа [NAAM]"))
        assertTrue(out, out.contains("Пациентът [NAAM]"))
    }

    @Test
    fun egnCheckDigit() {
        assertTrue(Anonymizer.isValidEgn("7501020018"))
        assertTrue(Anonymizer.isValidEgn("0252154518"))
        assertFalse(Anonymizer.isValidEgn("7501020019"))
        assertFalse(Anonymizer.isValidEgn("7513020018"))
        // Unrelated 10-digit numbers stay unless the check digit fits.
        assertTrue(Anonymizer.anonymize("Bestelnummer 1234567890").text.contains("1234567890"))
    }

    @Test
    fun ordinaryBulgarianTextIsKept() {
        val text = "Клиентът спи лошо и се чувства напрегнат. План: разговор утре в 10 часа."
        val out = Anonymizer.anonymize(text).text
        assertTrue(out, out.contains("спи лошо"))
        assertTrue(out, out.contains("План: разговор утре"))
    }
}
