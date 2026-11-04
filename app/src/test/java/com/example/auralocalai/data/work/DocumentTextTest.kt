package com.example.auralocalai.data.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocumentTextTest {

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> entries.forEach { (n, c) -> z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() } }
        return out.toByteArray()
    }

    @Test
    fun detectsFormats() {
        assertEquals(DocumentText.Kind.PDF, DocumentText.detect("%PDF-1.7".toByteArray(), "x"))
        assertEquals(DocumentText.Kind.IMAGE, DocumentText.detect(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()), "scan"))
        assertEquals(DocumentText.Kind.IMAGE, DocumentText.detect(byteArrayOf(0, 0, 0, 0x18) + "ftypheic".toByteArray() + ByteArray(8), null))
        assertEquals(DocumentText.Kind.RTF, DocumentText.detect("{\\rtf1 hoi}".toByteArray(), null))
        assertEquals(DocumentText.Kind.OLD_DOC, DocumentText.detect(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0), "a.doc"))
        assertEquals(DocumentText.Kind.DOCX, DocumentText.detect(DocxIO.write("T", null, "x"), "a.docx"))
        assertEquals(DocumentText.Kind.ODT, DocumentText.detect(zip("mimetype" to "application/vnd.oasis.opendocument.text", "content.xml" to "<x/>"), "a.bin"))
        assertEquals(DocumentText.Kind.HTML, DocumentText.detect("<!DOCTYPE html><p>a</p>".toByteArray(), "a"))
        assertEquals(DocumentText.Kind.TEXT, DocumentText.detect("gewoon tekst".toByteArray(), "notities.txt"))
    }

    @Test
    fun readsOdt() {
        val xml = "<office:document-content><office:body><office:text><text:h text:outline-level=\"1\">Rapport</text:h>" +
            "<text:p text:style-name=\"P1\">Cliënt<text:s/>is <text:span>rustig</text:span> &amp; helder.</text:p><text:p/>" +
            "<text:p>Regel<text:line-break/>twee<text:tab/>x</text:p></office:text></office:body></office:document-content>"
        val bytes = zip("mimetype" to "application/vnd.oasis.opendocument.text", "content.xml" to xml)
        assertEquals("Rapport\nCliënt is rustig & helder.\n\nRegel\ntwee\tx", DocumentText.extract(bytes, DocumentText.Kind.ODT))
    }

    @Test
    fun readsRtf() {
        val rtf = "{\\rtf1\\ansi\\ansicpg1252{\\fonttbl{\\f0 Calibri;}}{\\colortbl;\\red0\\green0\\blue0;}{\\*\\generator Word;}" +
            "\\f0\\fs22 Cli\\'ebnt slaapt slecht.\\par Plan:\\tab bespreken \\u8211?morgen\\par}"
        assertEquals("Cliënt slaapt slecht.\nPlan:\tbespreken –morgen", DocumentText.extract(rtf.toByteArray(), DocumentText.Kind.RTF))
    }

    @Test
    fun readsHtmlAndOldEncodings() {
        val html = "<html><head><title>x</title><style>p{}</style></head><body><h1>Titel</h1><p>Eerste&nbsp;alinea &amp; meer</p><ul><li>punt</li></ul></body></html>"
        assertEquals("Titel\nEerste alinea & meer\n• punt", DocumentText.extract(html.toByteArray(), DocumentText.Kind.HTML))
        val cp1252 = "Cliënt café".toByteArray(charset("windows-1252"))
        assertEquals("Cliënt café", DocumentText.decode(cp1252))
        assertEquals("Cliënt", DocumentText.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Cliënt".toByteArray()))
        assertTrue(DocumentText.extract("a\r\nb".toByteArray(), DocumentText.Kind.TEXT) == "a\nb")
    }

    @Test
    fun readsBulgarianCyrillic() {
        val bg = "Пациентът спи лошо.\nПлан: разговор утре."
        assertEquals(bg, DocumentText.decode(bg.toByteArray(charset("windows-1251"))))
        assertEquals(bg, DocumentText.decode(bg.toByteArray()))
        val rtf = "{\\rtf1\\ansi\\ansicpg1251 \\'cf\\'eb\\'e0\\'ed\\par \\u1055?\\u1083?\\u1072?\\u1085?}"
        assertEquals("План\nПлан", DocumentText.extract(rtf.toByteArray(), DocumentText.Kind.RTF))
        val odt = zip("content.xml" to "<text:p>Клиентът е спокоен.</text:p>")
        assertEquals("Клиентът е спокоен.", DocumentText.extract(odt, DocumentText.Kind.ODT))
        val docx = DocxIO.write("Доклад", null, "С – Субективно:\nСпи лошо.")
        assertTrue(DocumentText.extract(docx, DocumentText.Kind.DOCX)!!.contains("Спи лошо."))
    }
}
