package com.example.auralocalai.data.monitor

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/** What changed on a watched page since the previous check. */
@Serializable
data class PageChange(
    val detectedAt: Long,
    val added: List<String> = emptyList(),
    val removed: List<String> = emptyList()
)

/** A public page (guideline, standard, publication list) the app checks for content changes. */
@Serializable
data class WatchedPage(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val lastHash: String = "",
    /** Normalised text of the last successful check (needed to show what changed). */
    val lastText: String = "",
    val lastChecked: Long = 0L,
    val lastError: String = "",
    val lastChange: PageChange? = null
)

/**
 * Detects real content changes (unit-tested): the page text is normalised (dates, times and short
 * menu lines are ignored), hashed and compared paragraph by paragraph with the previous version.
 * A new date or a changed menu is therefore not reported as a changed guideline.
 */
object PageMonitor {

    const val MAX_TEXT = 40_000
    private const val MAX_LINES_SHOWN = 12

    private val DATE = Regex(
        "\\b\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}\\b|\\b\\d{4}-\\d{2}-\\d{2}\\b|\\b\\d{1,2}:\\d{2}\\b|" +
            "\\b\\d{1,2}\\s+(jan|feb|mrt|maart|apr|april|mei|jun|juni|jul|juli|aug|augustus|sep|sept|september|okt|oktober|nov|november|dec|december)\\w*\\.?(\\s+\\d{4})?",
        RegexOption.IGNORE_CASE
    )

    /** Paragraphs that carry content: at least 40 characters, dates and times removed, whitespace collapsed. */
    fun normalize(text: String): String =
        text.split(Regex("\n\\s*\n|\n"))
            .map { DATE.replace(it, " ").replace(Regex("\\s+"), " ").trim() }
            .filter { it.length >= 40 }
            .distinct()
            .joinToString("\n\n")
            .take(MAX_TEXT)

    fun hash(normalized: String): String =
        MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Paragraphs added and removed between two normalised texts. */
    fun diff(old: String, new: String, now: Long): PageChange {
        val before = old.split("\n\n").filter { it.isNotBlank() }.toSet()
        val after = new.split("\n\n").filter { it.isNotBlank() }
        return PageChange(
            detectedAt = now,
            added = after.filter { it !in before }.take(MAX_LINES_SHOWN),
            removed = before.filter { it !in after.toSet() }.take(MAX_LINES_SHOWN)
        )
    }

    /**
     * Result of one check. The first check only stores a baseline (no change reported).
     * Returns the updated page and whether a real change was found.
     */
    fun applyCheck(page: WatchedPage, pageText: String, now: Long): Pair<WatchedPage, Boolean> {
        val normalized = normalize(pageText)
        if (normalized.isBlank()) return page.copy(lastChecked = now, lastError = "no text") to false
        val newHash = hash(normalized)
        return when {
            page.lastHash.isEmpty() -> page.copy(lastHash = newHash, lastText = normalized, lastChecked = now, lastError = "") to false
            page.lastHash == newHash -> page.copy(lastChecked = now, lastError = "") to false
            else -> {
                val change = diff(page.lastText, normalized, now)
                val real = change.added.isNotEmpty() || change.removed.isNotEmpty()
                page.copy(
                    lastHash = newHash,
                    lastText = normalized,
                    lastChecked = now,
                    lastError = "",
                    lastChange = if (real) change else page.lastChange
                ) to real
            }
        }
    }

    /** Suggestions for Dutch mental-health and addiction-care guidance (the user can add any page). */
    val SUGGESTIONS = listOf(
        "GGZ Standaarden" to "https://www.ggzstandaarden.nl/zorgstandaarden",
        "Akwa GGZ" to "https://www.akwaggz.nl/",
        "NISPA" to "https://www.nispa.nl/",
        "Trimbos – kennis" to "https://www.trimbos.nl/kennis/",
        "Richtlijnendatabase" to "https://richtlijnendatabase.nl/"
    )
}
