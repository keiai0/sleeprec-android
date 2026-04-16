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
}
