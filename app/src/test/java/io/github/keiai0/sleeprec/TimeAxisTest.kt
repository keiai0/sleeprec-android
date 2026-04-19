package io.github.keiai0.sleeprec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeAxisTest {
    private val hour = 3_600_000L
    private val min = 60_000L

    // UTC(時差 0)で、指定した時刻(時・分)から始まる夜を作る
    private fun start(h: Int, m: Int = 0) = h * hour + m * min

    @Test fun eightHourNightHasTwoHourlyTicks() {
        val t = TimeAxis.ticks(start(23), 8 * hour, 0)
        assertTrue("count=${t.size}", t.size in 3..8)
        // 8 時間は 2 時間おき。23:00 開始なら、最初の目盛りは次の偶数時(0:00)で、1 時間後
        assertEquals("0:00", t.first().label)
        assertEquals(hour, t.first().offsetMs)
        assertEquals(listOf("0:00", "2:00", "4:00", "6:00"), t.map { it.label })
    }

    @Test fun startingOnATickPutsItAtZero() {
        val t = TimeAxis.ticks(start(23), 4 * hour, 0)
        assertEquals("23:00", t.first().label)
        assertEquals(0L, t.first().offsetMs)
    }

    @Test fun ticksAreOnRoundTimes() {
        val t = TimeAxis.ticks(start(23, 17), 8 * hour, 0)
        // 開始 23:17。最初の目盛りは、その次のちょうどの時刻
        assertTrue(t.first().offsetMs in 1..(2 * hour))
        assertTrue(t.all { it.label.endsWith(":00") })
    }

    @Test fun ticksCrossMidnight() {
        val labels = TimeAxis.ticks(start(22), 6 * hour, 0).map { it.label }
        assertTrue(labels.contains("0:00"))
        assertTrue(labels.indexOf("0:00") > labels.indexOf("23:00").coerceAtLeast(0) || !labels.contains("23:00"))
    }

    @Test fun ticksStayWithinTheNight() {
        val total = 7 * hour + 30 * min
        val t = TimeAxis.ticks(start(23, 30), total, 0)
        assertTrue(t.all { it.offsetMs in 0..total })
    }

    @Test fun tickCountIsBounded() {
        for (h in listOf(1L, 2L, 5L, 8L, 12L, 20L)) {
            val t = TimeAxis.ticks(start(23), h * hour, 0)
            assertTrue("h=$h count=${t.size}", t.size <= 9)
        }
    }

    @Test fun shortSessionUsesFineSteps() {
        val t = TimeAxis.ticks(start(23, 2), 40 * min, 0)
        assertTrue(t.size >= 2)
        assertTrue(t.zipWithNext().all { (a, b) -> b.offsetMs - a.offsetMs <= 15 * min })
    }

    @Test fun zoneOffsetShiftsLabels() {
        // UTC 14:00 開始、時差 +9 時間(日本)→ 現地 23:00
        val t = TimeAxis.ticks(14 * hour, 3 * hour, 9 * hour)
        assertEquals("23:00", t.first().label)
    }

    @Test fun emptyForZeroLength() = assertTrue(TimeAxis.ticks(0, 0, 0).isEmpty())

    @Test fun labelsHaveNoLeadingZeroForHours() {
        val t = TimeAxis.ticks(start(4, 30), 2 * hour, 0)
        assertTrue(t.any { it.label == "5:00" })
    }
}
