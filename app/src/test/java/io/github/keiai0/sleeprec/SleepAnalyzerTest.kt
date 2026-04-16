package io.github.keiai0.sleeprec

import io.github.keiai0.sleeprec.data.AudioEvent
import io.github.keiai0.sleeprec.data.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepAnalyzerTest {
    private val start = 1_000_000_000L
    private fun min(m: Int) = m * 60_000L

    private fun ev(type: EventType, atMin: Int, sec: Int = 30, offsetSec: Int = 0) =
        AudioEvent(sessionId = 1, startedAt = start + min(atMin) + offsetSec * 1000L, durationMs = sec * 1000L, maxDb = -20f, avgDb = -30f, type = type)

    /** 1 分の中に 30 秒の活動があれば覚醒。連続した分を覚醒にするイベント列。 */
    private fun awakeMinutes(from: Int, to: Int, type: EventType = EventType.OTHER) =
        (from until to).map { ev(type, it, sec = 30) }

    private fun analyze(totalMin: Int, events: List<AudioEvent>) =
        SleepAnalyzer.analyze(start, start + min(totalMin), events)

    @Test fun silentNightSleepsFromTheStart() {
        val m = analyze(480, emptyList()).metrics
        assertEquals(0, m.latencyMin)
        assertEquals(480, m.tstMin)
        assertEquals(0, m.wasoMin)
        assertEquals(0, m.wakeCount)
        assertEquals(1.0, m.efficiency, 0.0)
    }

    @Test fun latencyIsTimeUntilTenQuietMinutes() {
        // 0〜14 分は覚醒(活動あり)。15 分から静か → 入眠は 15 分
        val m = analyze(480, awakeMinutes(0, 15)).metrics
        assertEquals(15, m.latencyMin)
        assertEquals(465, m.tstMin)
        assertEquals(start + min(15), m.onsetAt)
    }

    @Test fun brokenQuietRunDelaysOnset() {
        // 静かな 5 分の後にまた活動 → その 5 分は入眠とみなさない
        val events = awakeMinutes(0, 5) + awakeMinutes(10, 12)
        assertEquals(12, analyze(480, events).metrics.latencyMin)
    }

    @Test fun midNightAwakeningIsCounted() {
        // 入眠後、200〜209 分に 10 分の覚醒
        val m = analyze(480, awakeMinutes(200, 210)).metrics
        assertEquals(10, m.wasoMin)
        assertEquals(1, m.wakeCount)
        assertEquals(470, m.tstMin)
    }

    @Test fun briefArousalIsNotAWakeCount() {
        // 3 分の覚醒は WASO には数えるが、5 分未満なので回数には数えない
        val m = analyze(480, awakeMinutes(100, 103)).metrics
        assertEquals(3, m.wasoMin)
        assertEquals(0, m.wakeCount)
    }

    @Test fun trailingAwakeIsNotWaso() {
        // 最後の 20 分は起きている(起床の準備)。睡眠の終わりは 460 分で、WASO には含めない
        val m = analyze(480, awakeMinutes(460, 480)).metrics
        assertEquals(0, m.wasoMin)
        assertEquals(460, m.tstMin)
        assertEquals(start + min(460), m.wakeAt)
    }

    @Test fun neverSleepingHasNoOnset() {
        val m = analyze(60, awakeMinutes(0, 60)).metrics
        assertNull(m.onsetAt)
        assertFalse(m.slept)
        assertEquals(0, m.tstMin)
    }

    @Test fun tooShortToSleep() {
        // 8 分しかない計測は、10 分の静かな時間を満たせない
        assertNull(analyze(8, emptyList()).metrics.onsetAt)
    }

    @Test fun snoringAndAnimalsAreNotActivity() {
        val events = (0 until 480).map { ev(EventType.SNORING, it) } + (0 until 480).map { ev(EventType.ANIMAL, it) }
        val m = analyze(480, events).metrics
        assertEquals(0, m.wasoMin)
        assertEquals(480, m.tstMin)
    }

    @Test fun coughAndFootstepsAreActivity() {
        assertEquals(10, analyze(480, awakeMinutes(100, 110, EventType.COUGH)).metrics.wasoMin)
        assertEquals(10, analyze(480, awakeMinutes(100, 110, EventType.FOOTSTEPS)).metrics.wasoMin)
    }

    @Test fun shortActivityDoesNotWake() {
        // 各分に 5 秒(10 秒未満)の活動 → 覚醒にはならない
        val events = (100 until 110).map { ev(EventType.COUGH, it, sec = 5) }
        assertEquals(0, analyze(480, events).metrics.wasoMin)
    }

    @Test fun eventSpanningMinutesIsSplit() {
        // 0:50 から 20 秒 = 前の分に 10 秒、次の分に 10 秒 → どちらも閾値に達して覚醒
        val e = listOf(ev(EventType.OTHER, 100, sec = 20, offsetSec = 50))
        assertEquals(2, analyze(480, e).metrics.wasoMin)
    }

    @Test fun stagesFollowTheCycleModel() {
        val a = analyze(480, emptyList())
        assertEquals(Stage.LIGHT, a.stages[3])   // 周期の始めは Light
        assertEquals(Stage.DEEP, a.stages[20])   // 最初の周期の Deep
        assertEquals(Stage.REM, a.stages[85])    // 最初の周期の終わりは REM
        assertEquals(Stage.REM, a.stages[430])   // 後半の周期は REM が長い
    }

    @Test fun quietNightHasPlausibleStageShares() {
        val m = analyze(480, emptyList()).metrics
        val deep = m.deepMin * 100.0 / m.tstMin
        val rem = m.remMin * 100.0 / m.tstMin
        val light = m.lightMin * 100.0 / m.tstMin
        assertTrue("deep=$deep", deep in 10.0..25.0)
        assertTrue("rem=$rem", rem in 15.0..30.0)
        assertTrue("light=$light", light in 45.0..70.0)
    }

    @Test fun deepShrinksAndRemGrowsAcrossTheNight() {
        val a = analyze(480, emptyList()).stages
        fun count(range: IntRange, s: Stage) = range.count { a[it] == s }
        assertTrue(count(0..179, Stage.DEEP) > count(300..479, Stage.DEEP))
        assertTrue(count(0..179, Stage.REM) < count(300..479, Stage.REM))
    }

    @Test fun nearActivityMakesLightSleep() {
        // 200 分に短い活動(覚醒にはならない) → 前後 5 分は Light
        val a = analyze(480, listOf(ev(EventType.COUGH, 200, sec = 5)))
        assertEquals(Stage.LIGHT, a.stages[198])
        assertEquals(Stage.LIGHT, a.stages[205])
        assertNotNull(a.stages[190])
        assertTrue(a.stages[190] != Stage.LIGHT)
    }

    @Test fun stageMinutesAddUp() {
        val m = analyze(480, awakeMinutes(200, 210)).metrics
        assertEquals(m.tstMin, m.lightMin + m.deepMin + m.remMin)
    }

    @Test fun stagesCoverTheWholeNight() = assertEquals(480, analyze(480, emptyList()).stages.size)
}
