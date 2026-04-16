package io.github.keiai0.sleeprec

import io.github.keiai0.sleeprec.data.ApneaCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApneaSummaryTest {
    private fun cand(silenceMs: Long) = ApneaCandidate(sessionId = 1, startedAt = 0, silenceMs = silenceMs, maxDb = -20f)
    private val hour = 3_600_000L

    @Test fun noCandidates() {
        val s = ApneaSummary.of(emptyList(), 8 * hour)
        assertEquals(0, s.count)
        assertEquals(0L, s.maxSilenceMs)
        assertEquals(ApneaLevel.NORMAL, s.level)
    }

    @Test fun countTotalAndMax() {
        val s = ApneaSummary.of(listOf(cand(10_000), cand(25_000), cand(15_000)), 8 * hour)
        assertEquals(3, s.count)
        assertEquals(50_000L, s.totalSilenceMs)
        assertEquals(25_000L, s.maxSilenceMs)
    }

    @Test fun perHourAndLevels() {
        // 8 時間で 40 回 = 5 回/時間 → 軽度
        assertEquals(ApneaLevel.MILD, ApneaSummary.of(List(40) { cand(10_000) }, 8 * hour).level)
        assertEquals(5.0, ApneaSummary.of(List(40) { cand(10_000) }, 8 * hour).perHour!!, 0.001)
        assertEquals(ApneaLevel.NORMAL, ApneaSummary.of(List(39) { cand(10_000) }, 8 * hour).level)
        assertEquals(ApneaLevel.MODERATE, ApneaSummary.of(List(120) { cand(10_000) }, 8 * hour).level)
        assertEquals(ApneaLevel.SEVERE, ApneaSummary.of(List(240) { cand(10_000) }, 8 * hour).level)
    }

    @Test fun shortSessionHasNoRate() {
        val s = ApneaSummary.of(List(5) { cand(10_000) }, hour / 2)
        assertEquals(5, s.count)
        assertNull(s.perHour)
        assertNull(s.level)
        assertFalse(s.showRiskNotice)
    }

    @Test fun riskNoticeFromModerate() {
        assertFalse(ApneaSummary.of(List(40) { cand(10_000) }, 8 * hour).showRiskNotice)
        assertTrue(ApneaSummary.of(List(120) { cand(10_000) }, 8 * hour).showRiskNotice)
    }
}
