package com.example.auralocalai.data.work

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

/**
 * Turns the bytes of a document into plain text. Pure Kotlin (no Android), so it can be unit tested.
 * PDF is not handled here: it needs Android's PdfRenderer and on-device OCR (see DocumentReader).
 */
object DocumentText {

    enum class Kind { PDF, DOCX, ODT, OLD_DOC, RTF, HTML, TEXT }

    /** Recognises the format from the first bytes, falling back to the file name. */
    fun detect(bytes: ByteArray, fileName: String?): Kind {
        val name = fileName.orEmpty().lowercase()
        fun starts(vararg b: Int) = bytes.size >= b.size && b.indices.all { bytes[it] == b[it].toByte() }
        return when {
            starts(0x25, 0x50, 0x44, 0x46) -> Kind.PDF // %PDF
            starts(0xD0, 0xCF, 0x11, 0xE0) -> Kind.OLD_DOC // old Word / OLE
            starts(0x50, 0x4B, 0x03, 0x04) -> if (name.endsWith(".odt") || zipHas(bytes, "content.xml") && !zipHas(bytes, "word/document.xml")) Kind.ODT else Kind.DOCX
            starts(0x7B, 0x5C, 0x72, 0x74, 0x66) -> Kind.RTF // {\rtf
            name.endsWith(".pdf") -> Kind.PDF
            name.endsWith(".html") || name.endsWith(".htm") || looksLikeHtml(bytes) -> Kind.HTML
            else -> Kind.TEXT
        }
    }

    /** Text for every kind except PDF (returns null for PDF and old .doc). */
    fun extract(bytes: ByteArray, kind: Kind, maxChars: Int = 60_000): String? = when (kind) {
        Kind.DOCX -> DocxIO.readText(bytes.inputStream(), maxChars)
        Kind.ODT -> zipEntry(bytes, "content.xml")?.let { odtXmlToText(it) }?.take(maxChars)
        Kind.RTF -> rtfToText(decode(bytes)).take(maxChars)
        Kind.HTML -> htmlToText(decode(bytes)).take(maxChars)
        Kind.TEXT -> decode(bytes).replace("\r\n", "\n").replace('\r', '\n').trim().take(maxChars)
        Kind.PDF, Kind.OLD_DOC -> null
    }

    /** UTF-8 (with or without BOM) or UTF-16 with BOM; otherwise Windows-1252, common for older Dutch files. */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, start, bytes.size - start)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
    }

    fun odtXmlToText(xml: String): String {
        val out = StringBuilder()
        val token = Regex("<text:(?:p|h)(?: [^>]*)?/>|<text:(?:p|h)(?: [^>]*)?>|</text:(?:p|h)>|<text:tab/>|<text:line-break/>|<text:s(?: text:c=\"(\\d+)\")?/>|<[^>]+>|([^<]+)")
        for (m in token.findAll(xml)) {
            val v = m.value
            when {
                v.startsWith("<text:p") || v.startsWith("<text:h") -> if (v.endsWith("/>")) out.append('\n')
                v == "</text:p>" || v == "</text:h>" -> out.append('\n')
                v == "<text:tab/>" -> out.append('\t')
                v == "<text:line-break/>" -> out.append('\n')
                v.startsWith("<text:s") && v.endsWith("/>") -> out.append(" ".repeat(m.groupValues[1].toIntOrNull() ?: 1))
                v.startsWith("<") -> {}
                else -> out.append(unescapeXml(v))
            }
        }
        return tidy(out.toString())
    }

    fun htmlToText(html: String): String {
        var t = html
            .replace(Regex("(?is)<(script|style|head)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|h[1-6]|li|tr|section|article)>"), "\n")
            .replace(Regex("(?i)<li[^>]*>"), "• ")
            .replace(Regex("<[^>]+>"), "")
        t = unescapeXml(t.replace("&nbsp;", " "))
        return tidy(t.lines().joinToString("\n") { it.replace(Regex("[ \\t]+"), " ").trim() })
    }

    /** Small RTF reader: keeps text, paragraphs, tabs and accented characters; skips fonts, colours and metadata. */
    fun rtfToText(rtf: String): String {
        val out = StringBuilder()
        val skipDestinations = setOf("fonttbl", "colortbl", "stylesheet", "info", "pict", "header", "footer", "listtable", "listoverridetable", "rsidtbl", "generator", "themedata", "colorschememapping", "latentstyles", "datastore", "xmlnstbl")
        // Stack of "skip this group" flags.
        val skip = ArrayDeque<Boolean>().apply { addLast(false) }
        var unicodeSkip = 1
        var pendingSkip = 0
        var i = 0
        val n = rtf.length
        while (i < n) {
            val c = rtf[i]
            when (c) {
                '{' -> { skip.addLast(skip.last()); i++ }
                '}' -> { if (skip.size > 1) skip.removeLast(); i++ }
                '\\' -> {
                    i++
                    if (i >= n) break
                    val d = rtf[i]
                    when {
                        d == '\\' || d == '{' || d == '}' -> { if (!skip.last()) out.append(d); i++ }
                        d == '*' -> { skip.removeLast(); skip.addLast(true); i++ }
                        d == '\'' -> {
                            val hex = rtf.substring(i + 1, minOf(i + 3, n))
                            i += 3
                            if (pendingSkip > 0) { pendingSkip--; continue }
                            hex.toIntOrNull(16)?.let { if (!skip.last()) out.append(String(byteArrayOf(it.toByte()), Charset.forName("windows-1252"))) }
                        }
                        d == '~' -> { if (!skip.last()) out.append(' '); i++ }
                        d == '\n' || d == '\r' -> { if (!skip.last()) out.append('\n'); i++ }
                        d.isLetter() -> {
                            val start = i
                            while (i < n && rtf[i].isLetter()) i++
                            val word = rtf.substring(start, i)
                            val numStart = i
                            if (i < n && (rtf[i] == '-' || rtf[i].isDigit())) { i++; while (i < n && rtf[i].isDigit()) i++ }
                            val num = rtf.substring(numStart, i).toIntOrNull()
                            if (i < n && rtf[i] == ' ') i++
                            if (word in skipDestinations) { skip.removeLast(); skip.addLast(true); continue }
                            if (skip.last()) continue
                            when (word) {
                                "par", "line", "sect", "page" -> out.append('\n')
                                "tab" -> out.append('\t')
                                "cell" -> out.append('\t')
                                "row" -> out.append('\n')
                                "uc" -> unicodeSkip = num ?: 1
                                "u" -> if (num != null) {
                                    out.append((if (num < 0) num + 65536 else num).toChar())
                                    pendingSkip = unicodeSkip
                                }
                                "emdash" -> out.append('—')
                                "endash" -> out.append('–')
                                "bullet" -> out.append('•')
                                "lquote", "rquote" -> out.append('\'')
                                "ldblquote", "rdblquote" -> out.append('"')
                            }
                        }
                        else -> i++
                    }
                }
                '\r', '\n' -> i++
                else -> {
                    if (pendingSkip > 0) pendingSkip-- else if (!skip.last()) out.append(c)
                    i++
                }
            }
        }
        return tidy(out.toString())
    }

    private fun tidy(text: String): String =
        text.lines().joinToString("\n") { it.trimEnd() }.replace(Regex("\n{3,}"), "\n\n").trim()

    private fun unescapeXml(s: String): String =
        s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")
            .replace(Regex("&#(x?)([0-9a-fA-F]+);")) { m ->
                val code = if (m.groupValues[1].isEmpty()) m.groupValues[2].toIntOrNull() else m.groupValues[2].toIntOrNull(16)
                code?.let { String(Character.toChars(it)) } ?: ""
            }
            .replace("&amp;", "&")

    private fun looksLikeHtml(bytes: ByteArray): Boolean {
        val head = String(bytes, 0, minOf(bytes.size, 512), Charsets.ISO_8859_1).trimStart().lowercase()
        return head.startsWith("<!doctype html") || head.startsWith("<html")
    }

    private fun zipHas(bytes: ByteArray, entryName: String): Boolean =
        runCatching {
            ZipInputStream(bytes.inputStream()).use { zip ->
                generateSequence { zip.nextEntry }.any { it.name == entryName }
            }
        }.getOrDefault(false)

    private fun zipEntry(bytes: ByteArray, entryName: String): String? =
        ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.firstOrNull { it.name == entryName }?.let { zip.readBytes().toString(Charsets.UTF_8) }
        }
}
