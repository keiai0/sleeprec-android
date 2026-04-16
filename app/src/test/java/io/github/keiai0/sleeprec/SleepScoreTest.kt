package io.github.keiai0.sleeprec

import io.github.keiai0.sleeprec.data.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class SleepScoreTest {
    private val h = 3_600_000L
    private fun metrics(
        tst: Int = 450, tib: Int = 480, latency: Int = 15, waso: Int = 10, wakes: Int = 0,
    ) = SleepMetrics(tib, latency, tst, waso, wakes, 0, 0, 0, onsetAt = 1L, wakeAt = 2L)

    private val goal = Thresholds.GOAL_SLEEP_MS // 7.5 時間

    // --- Duration ---
    @Test fun durationBelowFourHoursIsZero() = assertEquals(0.0, SleepScore.durationRatio(4 * h, goal), 0.0)

    @Test fun durationLinearBetweenFourHoursAndGoal() =
        assertEquals(0.5, SleepScore.durationRatio((4 * h + goal) / 2, goal), 1e-9)

    @Test fun durationFullBetweenGoalAndNine() {
        assertEquals(1.0, SleepScore.durationRatio(goal, goal), 0.0)
        assertEquals(1.0, SleepScore.durationRatio(9 * h, goal), 0.0)
    }

    @Test fun durationPenaltyAboveNineHasFloor() {
        assertEquals(0.9, SleepScore.durationRatio(10 * h, goal), 1e-9)
        assertEquals(0.7, SleepScore.durationRatio(14 * h, goal), 1e-9)
    }

    // --- Restoration ---
    @Test fun restorationPerfect() =
        assertEquals(1.0, SleepScore.restorationRatio(metrics(tst = 450, tib = 500, latency = 20, waso = 10)), 1e-9)

    @Test fun restorationWorst() =
        assertEquals(0.0, SleepScore.restorationRatio(metrics(tst = 300, tib = 480, latency = 60, waso = 60, wakes = 5)), 1e-9)

    @Test fun wakeCountBands() {
        fun r(w: Int) = SleepScore.restorationRatio(metrics(tib = 450, latency = 0, waso = 0, wakes = w))
        assertEquals(1.0, r(1), 1e-9)
        assertEquals(0.9, r(2), 1e-9) // 0.2 * 0.5 だけ減る
        assertEquals(0.9, r(3), 1e-9)
        assertEquals(0.8, r(4), 1e-9)
    }

    @Test fun efficiencyBoundaries() {
        // 効率 85% 以上で 100%、74% 以下で 0%
        assertEquals(1.0, SleepScore.restorationRatio(metrics(tst = 425, tib = 500, latency = 0, waso = 0)), 1e-9)
        assertEquals(0.6, SleepScore.restorationRatio(metrics(tst = 370, tib = 500, latency = 0, waso = 0)), 1e-9)
    }

    // --- Consistency ---
    private fun night(dayIndex: Int, bedMinFromMidnight: Int, wakeMinFromMidnight: Int): NightTimes {
        val zone = TimeZone.getDefault()
        val day0 = 1_700_000_000_000L - ((1_700_000_000_000L + zone.getOffset(1_700_000_000_000L)) % (24 * h))
        val bed = day0 + dayIndex * 24 * h + bedMinFromMidnight * 60_000L
        return NightTimes(bed, day0 + (dayIndex + 1) * 24 * h + wakeMinFromMidnight * 60_000L)
    }

    @Test fun consistencyNeedsThreeNights() =
        assertNull(SleepScore.consistencyRatio(listOf(night(0, 1380, 420), night(1, 1380, 420))))

    @Test fun identicalTimesAreFullyConsistent() {
        val nights = (0 until 5).map { night(it, 1380, 420) }
        assertEquals(1.0, SleepScore.consistencyRatio(nights)!!, 1e-9)
    }

    @Test fun midnightCrossingIsHandled() {
        // 23:30 と 00:30(=24:30)の就寝は 1 時間差。0:00 をまたいでも、差は 1 時間として扱われる
        val nights = listOf(night(0, 1410, 420), night(1, 1470, 420), night(2, 1410, 420), night(3, 1470, 420))
        val r = SleepScore.consistencyRatio(nights)!!
        // SD = 30 分 → 就寝は 100%、起床は 100% → 1.0
        assertEquals(1.0, r, 1e-9)
    }

    @Test fun veryIrregularIsLow() {
        val nights = listOf(night(0, 1200, 300), night(1, 1500, 600), night(2, 1260, 360), night(3, 1560, 660))
        assertTrue(SleepScore.consistencyRatio(nights)!! < 0.2)
    }

    // --- 合計 ---
    private val regular = (0 until 7).map { night(it, 1380, 420) }

    @Test fun perfectNightScoresHundred() {
        val r = SleepScore.compute(SessionStatus.COMPLETED, metrics(tst = 450, tib = 500, latency = 10, waso = 5), regular, mood = 5)
        assertEquals(100, r.total)
        assertEquals(ScoreBand.EXCELLENT, r.band)
        assertEquals(4, r.parts.size)
    }

    @Test fun missingConsistencyAndFeelingAreRedistributed() {
        val r = SleepScore.compute(SessionStatus.COMPLETED, metrics(tst = 450, tib = 500, latency = 10, waso = 5), emptyList(), mood = null)
        assertEquals(100, r.total) // Duration と Restoration が満点なら、残りを 100 点に換算して 100
        assertEquals(listOf(ScorePart.Name.CONSISTENCY, ScorePart.Name.FEELING), r.skipped)
        assertEquals(70, r.parts.sumOf { it.max })
    }

    @Test fun moodConvertsToPoints() {
        val base = { mood: Int -> SleepScore.compute(SessionStatus.COMPLETED, metrics(tst = 450, tib = 500, latency = 10, waso = 5), regular, mood).parts.first { it.name == ScorePart.Name.FEELING } }
        assertEquals(0.0, base(1).points, 1e-9)
        assertEquals(5.0, base(3).points, 1e-9)
        assertEquals(10.0, base(5).points, 1e-9)
    }

    @Test fun bands() {
        assertEquals(ScoreBand.EXCELLENT, SleepScore.bandOf(85))
        assertEquals(ScoreBand.PRETTY_GOOD, SleepScore.bandOf(84))
        assertEquals(ScoreBand.PRETTY_GOOD, SleepScore.bandOf(70))
        assertEquals(ScoreBand.FAIR, SleepScore.bandOf(69))
        assertEquals(ScoreBand.FAIR, SleepScore.bandOf(55))
        assertEquals(ScoreBand.POOR, SleepScore.bandOf(54))
    }

    // --- スコアを出さない条件(FR-4.4) ---
    @Test fun shortSleepHasNoScore() =
        assertEquals(NoScoreReason.SHORT_SLEEP, SleepScore.compute(SessionStatus.SHORT_SLEEP, metrics(), regular, 3).reason)

    @Test fun interruptedHasNoScore() =
        assertEquals(NoScoreReason.INTERRUPTED, SleepScore.compute(SessionStatus.INTERRUPTED, metrics(), regular, 3).reason)

    @Test fun underFourHoursHasNoScore() {
        val r = SleepScore.compute(SessionStatus.COMPLETED, metrics(tst = 239, tib = 300), regular, 3)
        assertEquals(NoScoreReason.TOO_SHORT, r.reason)
        assertNull(r.total)
    }

    @Test fun exactlyFourHoursGetsScore() =
        assertNotNull(SleepScore.compute(SessionStatus.COMPLETED, metrics(tst = 240, tib = 300), regular, 3).total)

    @Test fun neverSleptHasNoScore() {
        val m = SleepMetrics(480, 480, 0, 0, 0, 0, 0, 0, onsetAt = null, wakeAt = null)
        assertEquals(NoScoreReason.NO_SLEEP, SleepScore.compute(SessionStatus.COMPLETED, m, regular, 3).reason)
    }
}
