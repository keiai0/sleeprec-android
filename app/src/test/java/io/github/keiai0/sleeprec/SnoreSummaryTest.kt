package io.github.keiai0.sleeprec

import io.github.keiai0.sleeprec.data.AudioEvent
import io.github.keiai0.sleeprec.data.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnoreSummaryTest {
    private fun ev(type: EventType, startedAt: Long, durationMs: Long, maxDb: Float, avgDb: Float) =
        AudioEvent(sessionId = 1, startedAt = startedAt, durationMs = durationMs, maxDb = maxDb, avgDb = avgDb, type = type)

    @Test fun noSnoresIsNull() =
        assertNull(SnoreSummary.of(listOf(ev(EventType.COUGH, 0, 1000, -20f, -30f))))

    @Test fun emptyIsNull() = assertNull(SnoreSummary.of(emptyList()))

    @Test fun aggregatesOnlySnores() {
        val s = SnoreSummary.of(
            listOf(
                ev(EventType.SNORING, 3000, 10_000, -30f, -40f),
                ev(EventType.COUGH, 1000, 2_000, -5f, -10f),
                ev(EventType.SNORING, 2000, 30_000, -25f, -50f),
            )
        )!!
        assertEquals(2, s.count)
        assertEquals(40_000L, s.totalMs)
        assertEquals(-25f, s.maxDb, 0f)
        // 長さで重み付け: (-40*10 + -50*30) / 40 = -47.5
        assertEquals(-47.5f, s.avgDb, 0.001f)
        assertEquals(listOf(2000L, 3000L), s.times)
    }

    @Test fun levelBoundaries() {
        assertEquals(SnoreLevel.QUIET, SnoreLevel.of(-40f))
        assertEquals(SnoreLevel.QUIET, SnoreLevel.of(-33.1f))
        assertEquals(SnoreLevel.LIGHT, SnoreLevel.of(-33f))
        assertEquals(SnoreLevel.LIGHT, SnoreLevel.of(-25.1f))
        assertEquals(SnoreLevel.LOUD, SnoreLevel.of(-25f))
        assertEquals(SnoreLevel.LOUD, SnoreLevel.of(-15.1f))
        assertEquals(SnoreLevel.VERY_LOUD, SnoreLevel.of(-15f))
        assertEquals(SnoreLevel.VERY_LOUD, SnoreLevel.of(0f))
    }

    @Test fun summaryCountsLevelsAndFindsMax() {
        val s = SnoreSummary.of(
            listOf(
                ev(EventType.SNORING, 1000, 5_000, -38f, -45f),   // 静か
                ev(EventType.SNORING, 2000, 5_000, -30f, -45f),   // 軽い
                ev(EventType.SNORING, 3000, 5_000, -29f, -45f),   // 軽い
                ev(EventType.SNORING, 4000, 5_000, -20f, -45f),   // 大きい
                ev(EventType.COUGH, 5000, 5_000, -5f, -20f),      // いびきではない(対象外)
            )
        )!!
        assertEquals(1, s.levelCounts.getValue(SnoreLevel.QUIET))
        assertEquals(2, s.levelCounts.getValue(SnoreLevel.LIGHT))
        assertEquals(1, s.levelCounts.getValue(SnoreLevel.LOUD))
        assertEquals(0, s.levelCounts.getValue(SnoreLevel.VERY_LOUD))
        assertEquals(SnoreLevel.LOUD, s.maxLevel)
        assertEquals(s.count, s.levelCounts.values.sum())
    }
}

