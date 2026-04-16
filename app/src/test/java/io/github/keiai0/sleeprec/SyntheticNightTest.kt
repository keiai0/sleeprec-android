package io.github.keiai0.sleeprec

import io.github.keiai0.sleeprec.data.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticNightTest {
    private val start = 1_700_000_000_000L

    @Test fun sameSeedGivesSameNight() {
        val a = SyntheticNightGenerator.generate(start, seed = 7)
        val b = SyntheticNightGenerator.generate(start, seed = 7)
        assertEquals(a.events.map { it.startedAt to it.type }, b.events.map { it.startedAt to it.type })
    }

    @Test fun samplesCoverTheWholeNight() {
        val n = SyntheticNightGenerator.generate(start, durationMin = 480)
        assertEquals(480 * 60, n.samples.size)
        assertEquals(start + 480 * 60_000L, n.endedAt)
    }

    @Test fun analysisLooksLikeARealNight() {
        for (seed in 1..20) {
            val night = SyntheticNightGenerator.generate(start, seed = seed)
            val m = SleepAnalyzer.analyze(night.startedAt, night.endedAt, night.events).metrics
            assertTrue("seed=$seed latency=${m.latencyMin}", m.latencyMin in 8..21)
            assertTrue("seed=$seed tst=${m.tstMin}", m.tstMin in 360..470)
            assertTrue("seed=$seed wakes=${m.wakeCount}", m.wakeCount >= 1)
            assertTrue("seed=$seed waso=${m.wasoMin}", m.wasoMin in 6..40)
        }
    }

    @Test fun scoreIsComputedForANormalNight() {
        val night = SyntheticNightGenerator.generate(start, seed = 3)
        val m = SleepAnalyzer.analyze(night.startedAt, night.endedAt, night.events).metrics
        val r = SleepScore.compute(SessionStatus.COMPLETED, m, emptyList(), mood = null)
        assertNotNull(r.total)
        assertTrue(r.total!! in 40..100)
    }
}
