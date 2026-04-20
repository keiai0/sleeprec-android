package io.github.keiai0.sleeprec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsPeriodTest {
    private val sat = LocalDate.of(2026, 9, 19) // 土曜日

    @Test fun weekStartsOnMondayAndEndsOnSunday() {
        val p = StatsPeriod.of(PeriodKind.WEEK, sat)
        assertEquals(LocalDate.of(2026, 9, 14), p.start)
        assertEquals(LocalDate.of(2026, 9, 20), p.end)
        assertEquals(7, p.days)
    }

    @Test fun mondayAndSundayBelongToTheirOwnWeek() {
        assertEquals(LocalDate.of(2026, 9, 14), StatsPeriod.of(PeriodKind.WEEK, LocalDate.of(2026, 9, 14)).start)
        assertEquals(LocalDate.of(2026, 9, 14), StatsPeriod.of(PeriodKind.WEEK, LocalDate.of(2026, 9, 20)).start)
    }

    @Test fun monthCoversFirstToLast() {
        val p = StatsPeriod.of(PeriodKind.MONTH, sat)
        assertEquals(LocalDate.of(2026, 9, 1), p.start)
        assertEquals(LocalDate.of(2026, 9, 30), p.end)
        assertEquals(30, p.days)
    }

    @Test fun februaryLeapYear() = assertEquals(29, StatsPeriod.of(PeriodKind.MONTH, LocalDate.of(2028, 2, 10)).days)

    @Test fun previousAndNextWeek() {
        val p = StatsPeriod.of(PeriodKind.WEEK, sat)
        assertEquals(LocalDate.of(2026, 9, 7), p.previous().start)
        assertEquals(LocalDate.of(2026, 9, 21), p.next().start)
        assertEquals(p.start, p.next().previous().start)
    }

    @Test fun monthNavigationAcrossYears() {
        val jan = StatsPeriod.of(PeriodKind.MONTH, LocalDate.of(2027, 1, 15))
        assertEquals(LocalDate.of(2026, 12, 1), jan.previous().start)
        assertEquals(LocalDate.of(2027, 2, 1), jan.next().start)
    }

    @Test fun contains() {
        val p = StatsPeriod.of(PeriodKind.WEEK, sat)
        assertTrue(LocalDate.of(2026, 9, 14) in p)
        assertTrue(LocalDate.of(2026, 9, 20) in p)
        assertFalse(LocalDate.of(2026, 9, 21) in p)
        assertFalse(LocalDate.of(2026, 9, 13) in p)
    }
}

class NightDayTest {
    private val hour = 3_600_000L
    private val jst = 9 * hour
    // UTC の t 時(2026-09-18 を 0 時とする)。日本時間で見る
    private fun jstMs(day: Int, h: Int, m: Int = 0): Long =
        LocalDate.of(2026, 9, day).toEpochDay() * 24 * hour + h * hour + m * 60_000L - jst

    @Test fun elevenPmBelongsToTheNextMorning() =
        assertEquals(LocalDate.of(2026, 9, 19), NightDay.of(jstMs(18, 23), jst))

    @Test fun lateNightStartBelongsToThatMorning() =
        assertEquals(LocalDate.of(2026, 9, 19), NightDay.of(jstMs(19, 3), jst))

    @Test fun beforeCutoffIsTheSameDay() =
        assertEquals(LocalDate.of(2026, 9, 19), NightDay.of(jstMs(19, 14, 59), jst))

    @Test fun afterCutoffIsTheNextDay() =
        assertEquals(LocalDate.of(2026, 9, 20), NightDay.of(jstMs(19, 15), jst))

    @Test fun differentCutoff() =
        assertEquals(LocalDate.of(2026, 9, 19), NightDay.of(jstMs(19, 17), jst, cutoffHour = 18))
}

class TimeMathTest {
    private val hour = 3_600_000L
    private fun utc(day: Int, h: Int, m: Int = 0) = java.time.LocalDate.of(2026, 9, day).toEpochDay() * 24 * hour + h * hour + m * 60_000L

    @Test fun meanAcrossMidnight() {
        // 23:30 と 0:30 の平均は 0:00(12:00 ではない)
        val mean = TimeMath.meanMinuteOfDay(listOf(utc(18, 23, 30), utc(20, 0, 30)), listOf(0, 0))
        assertEquals(0, mean)
    }

    @Test fun meanOfMorningTimes() {
        val mean = TimeMath.meanMinuteOfDay(listOf(utc(19, 6), utc(20, 7)), listOf(0, 0))
        assertEquals(6 * 60 + 30, mean)
    }

    @Test fun zoneOffsetIsApplied() {
        // UTC 14:00 = 日本の 23:00
        assertEquals(23 * 60, TimeMath.meanMinuteOfDay(listOf(utc(18, 14)), listOf(9 * hour)))
    }

    @Test fun emptyIsNull() = assertNull(TimeMath.meanMinuteOfDay(emptyList(), emptyList()))

    @Test fun formatHasNoLeadingZeroForHours() {
        assertEquals("7:05", TimeMath.format(7 * 60 + 5))
        assertEquals("0:00", TimeMath.format(0))
        assertEquals("23:59", TimeMath.format(23 * 60 + 59))
    }

    @Test fun noonRoundTrip() {
        for (m in listOf(0, 359, 720, 1000, 1439)) {
            val epoch = m * 60_000L
            assertEquals(m, TimeMath.toMinuteOfDay(TimeMath.minutesFromNoon(epoch, 0)))
        }
    }
}

class PeriodStatsTest {
    private val week = StatsPeriod.of(PeriodKind.WEEK, LocalDate.of(2026, 9, 19))
    private val hour = 3_600_000L
    private fun night(
        day: Int, tst: Int = 420, score: Int? = 80, snoreCount: Int = 2, snoreMs: Long = 60_000, maxDb: Float? = -30f,
        bedHour: Int = 23, wakeHour: Int = 7, eff: Double = 0.9,
    ): NightSummary {
        val d = LocalDate.of(2026, 9, day)
        val bed = (d.toEpochDay() - 1) * 24 * hour + bedHour * hour
        val wake = d.toEpochDay() * 24 * hour + wakeHour * hour
        return NightSummary(d, bed, wake, 0, tst, eff, score, snoreCount, snoreMs, maxDb)
    }

    @Test fun noNightsGivesEmptyStatsButFullDayList() {
        val s = PeriodStats.of(emptyList(), week)
        assertEquals(0, s.nights)
        assertNull(s.avgTstMin)
        assertNull(s.maxDb)
        assertEquals(7, s.points.size)
        assertTrue(s.points.all { it.tstMin == null })
    }

    @Test fun averages() {
        val s = PeriodStats.of(listOf(night(15, tst = 400, score = 70), night(16, tst = 440, score = 90), night(17, tst = 420, score = 80)), week)
        assertEquals(3, s.nights)
        assertEquals(420, s.avgTstMin)
        assertEquals(80, s.avgScore)
        assertEquals(0.9, s.avgEfficiency!!, 1e-9)
    }

    @Test fun averageBedAndWakeTimes() {
        val s = PeriodStats.of(listOf(night(15, bedHour = 23, wakeHour = 6), night(16, bedHour = 1, wakeHour = 8)), week)
        // 23:00 と 1:00 の平均は 0:00、6:00 と 8:00 の平均は 7:00
        assertEquals(0, s.avgBedMinuteOfDay)
        assertEquals(7 * 60, s.avgWakeMinuteOfDay)
    }

    @Test fun snoreAveragesAndMaxDb() {
        val s = PeriodStats.of(listOf(night(15, snoreCount = 4, snoreMs = 120_000, maxDb = -35f), night(16, snoreCount = 0, snoreMs = 0, maxDb = -20f)), week)
        assertEquals(2.0, s.avgSnoreCount!!, 1e-9)
        assertEquals(60_000L, s.avgSnoreMs)
        assertEquals(-20f, s.maxDb!!, 0f)
    }

    @Test fun nightsThatNeverSleptAreExcluded() {
        val s = PeriodStats.of(listOf(night(15, tst = 0, score = null), night(16, tst = 400)), week)
        assertEquals(1, s.nights)
        assertEquals(400, s.avgTstMin)
    }

    @Test fun nightsOutsideThePeriodAreIgnored() {
        val s = PeriodStats.of(listOf(night(13), night(21), night(17)), week)
        assertEquals(1, s.nights)
    }

    @Test fun scoreAverageSkipsNightsWithoutScore() {
        val s = PeriodStats.of(listOf(night(15, score = null), night(16, score = 60)), week)
        assertEquals(60, s.avgScore)
        assertEquals(2, s.nights)
    }

    @Test fun pointsPickTheLongestNightOfTheDay() {
        val s = PeriodStats.of(listOf(night(16, tst = 90, score = 50), night(16, tst = 400, score = 75)), week)
        val p = s.points[LocalDate.of(2026, 9, 16).toEpochDay().minus(week.start.toEpochDay()).toInt()]
        assertEquals(400, p.tstMin)
        assertEquals(75, p.score)
    }

    @Test fun pointsAreOnePerDayInOrder() {
        val s = PeriodStats.of(listOf(night(15)), week)
        assertEquals(7, s.points.size)
        assertEquals(week.start, s.points.first().date)
        assertEquals(week.end, s.points.last().date)
    }
}

class ImageExportTest {
    @Test fun weeklyFileName() =
        assertEquals("SleepRec_stats_week_20260914.png", ImageExport.fileName(StatsPeriod.of(PeriodKind.WEEK, LocalDate.of(2026, 9, 19))))

    @Test fun monthlyFileName() =
        assertEquals("SleepRec_stats_month_20260901.png", ImageExport.fileName(StatsPeriod.of(PeriodKind.MONTH, LocalDate.of(2026, 9, 19))))

    @Test fun fileNameIsAscii() =
        assertTrue(ImageExport.fileName(StatsPeriod.of(PeriodKind.WEEK, LocalDate.of(2026, 1, 1))).all { it.code < 128 })
}
