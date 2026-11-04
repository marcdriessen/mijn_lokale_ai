package com.example.auralocalai.data.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageMonitorTest {

    private val p1 = "De zorgstandaard alcohol beschrijft de behandeling van stoornissen in alcoholgebruik."
    private val p2 = "Bij ernstige onthouding wordt een benzodiazepine volgens protocol overwogen door de arts."

    @Test
    fun firstCheckIsBaselineAndDatesAreIgnored() {
        val page = WatchedPage("1", "GGZ", "https://x")
        val (base, changed0) = PageMonitor.applyCheck(page, "Menu\n$p1\n\nBijgewerkt op 3 november 2026: $p2", 1)
        assertFalse(changed0)
        assertTrue(base.lastHash.isNotEmpty())
        val (same, changed1) = PageMonitor.applyCheck(base, "Home\n$p1\n\nBijgewerkt op 12 december 2026: $p2", 2)
        assertFalse(changed1)
        assertEquals(base.lastHash, same.lastHash)
    }

    @Test
    fun realChangeIsReportedWithParagraphs() {
        val page = PageMonitor.applyCheck(WatchedPage("1", "GGZ", "https://x"), "$p1\n\n$p2", 1).first
        val newP = "Nieuw: de standaard adviseert nu ook digitale terugvalpreventie na ontslag uit de kliniek."
        val (updated, changed) = PageMonitor.applyCheck(page, "$p1\n\n$newP", 2)
        assertTrue(changed)
        assertEquals(listOf(newP), updated.lastChange!!.added)
        assertEquals(listOf(p2), updated.lastChange!!.removed)
    }
}
