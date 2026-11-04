package com.example.auralocalai.data.work

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Minimal Word (.docx) support without extra libraries: reads the plain text of a document
 * and writes a simple document with headings and paragraphs. Everything happens on the phone.
 */
object DocxIO {

    const val MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    /** True when the bytes start like a zip file (a .docx is a zip). */
    fun looksLikeDocx(head: ByteArray): Boolean =
        head.size >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() &&
            head[2].toInt() == 3 && head[3].toInt() == 4

    /** Returns the text of word/document.xml, one line per paragraph; null when it is not a Word file. */
    fun readText(input: InputStream, maxChars: Int = 60_000): String? {
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return null
                if (entry.name == "word/document.xml") {
                    val xml = zip.readBytes().toString(Charsets.UTF_8)
                    return xmlToText(xml).take(maxChars)
                }
            }
        }
    }

    /** Converts WordprocessingML to plain text: paragraphs become lines, tabs and breaks are kept. */
    fun xmlToText(xml: String): String {
        val out = StringBuilder()
        val token = Regex("<w:p[ >/]|</w:p>|<w:tab/>|<w:br/>|<w:cr/>|<w:t(?: [^>]*)?>([^<]*)</w:t>|<w:t/>")
        var paragraphOpen = false
        for (m in token.findAll(xml)) {
            val v = m.value
            when {
                v.startsWith("<w:p") -> {
                    if (paragraphOpen) out.append('\n')
                    paragraphOpen = !v.endsWith("/")
                    if (v == "<w:p/") out.append('\n')
                }
                v == "</w:p>" -> { out.append('\n'); paragraphOpen = false }
                v == "<w:tab/>" -> out.append('\t')
                v == "<w:br/>" || v == "<w:cr/>" -> out.append('\n')
                else -> out.append(unescape(m.groupValues[1]))
            }
        }
        return out.toString()
            .lines().joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    /**
     * Builds a .docx. [title] becomes a large bold heading; in [body] lines that look like a
     * section heading (for example "S – Subjectief:" or "**Plan**") are made bold when [detectHeadings] is on.
     */
    fun write(title: String, subtitle: String?, body: String, detectHeadings: Boolean = true): ByteArray {
        val paragraphs = StringBuilder()
        paragraphs.append(paragraph(title, bold = true, sizeHalfPoints = 32))
        if (!subtitle.isNullOrBlank()) paragraphs.append(paragraph(subtitle, italic = true, sizeHalfPoints = 20))
        paragraphs.append(paragraph("", bold = false))
        cleanMarkdown(body).lines().forEach { line ->
            val heading = detectHeadings && isHeading(line)
            paragraphs.append(paragraph(line, bold = heading, sizeHalfPoints = if (heading) 26 else null, spaceBefore = heading))
        }
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$paragraphs<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1418" w:right="1418" w:bottom="1418" w:left="1418" w:header="709" w:footer="709" w:gutter="0"/></w:sectPr></w:body></w:document>"""
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", CONTENT_TYPES)
            put("_rels/.rels", ROOT_RELS)
            put("word/_rels/document.xml.rels", DOC_RELS)
            put("word/styles.xml", STYLES)
            put("word/document.xml", document)
        }
        return bytes.toByteArray()
    }

    /** Removes markdown markers (**, ##, leading "- ") that small models often add. */
    fun cleanMarkdown(text: String): String =
        text.lines().joinToString("\n") { line ->
            line.replace("**", "").replace(Regex("^#{1,6}\\s*"), "").replace(Regex("^\\s*[-*]\\s+"), "• ")
        }.trim()

    /** A short line that starts with a SOEP/SOAP/SBAR letter or section name and (usually) ends in a colon. */
    fun isHeading(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty() || t.length > 40) return false
        val names = listOf(
            "subjectief", "objectief", "evaluatie", "plan", "subjective", "objective", "evaluation", "assessment",
            "situatie", "achtergrond", "beoordeling", "aanbeveling", "situation", "background", "recommendation",
            "субективно", "обективно", "оценка", "план"
        )
        val lower = t.lowercase().trimEnd(':', ' ')
        if (Regex("^[SOEAPBR]( ?[-–:(].*)?$").matches(t) && (t.length <= 2 || t.endsWith(":") || t.endsWith(")") || names.any { lower.contains(it) })) return true
        return names.any { lower == it } || (t.endsWith(":") && names.any { lower.endsWith(it) })
    }

    private fun paragraph(text: String, bold: Boolean = false, italic: Boolean = false, sizeHalfPoints: Int? = null, spaceBefore: Boolean = false): String {
        val pPr = if (spaceBefore) "<w:pPr><w:spacing w:before=\"200\"/></w:pPr>" else ""
        if (text.isEmpty()) return "<w:p>$pPr</w:p>"
        val rPr = buildString {
            if (bold) append("<w:b/>")
            if (italic) append("<w:i/>")
            if (sizeHalfPoints != null) append("<w:sz w:val=\"$sizeHalfPoints\"/>")
        }.let { if (it.isEmpty()) "" else "<w:rPr>$it</w:rPr>" }
        val runs = text.split('\t').joinToString("<w:tab/>") { "<w:t xml:space=\"preserve\">${escape(it)}</w:t>" }
        return "<w:p>$pPr<w:r>$rPr$runs</w:r></w:p>"
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
            .filter { it == '\t' || it == '\n' || it >= ' ' }

    private fun unescape(s: String): String =
        s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
            .replace(Regex("&#(x?)([0-9a-fA-F]+);")) { m ->
                val code = if (m.groupValues[1].isEmpty()) m.groupValues[2].toIntOrNull() else m.groupValues[2].toIntOrNull(16)
                code?.let { String(Character.toChars(it)) } ?: ""
            }
            .replace("&amp;", "&")

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""

    private const val DOC_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri" w:eastAsia="Calibri"/><w:sz w:val="22"/><w:lang w:val="nl-NL"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="80" w:line="276" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style></w:styles>"""
}
