package io.github.keiai0.sleeprec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class LoudnessTest {
    private fun pcm(samples: List<Int>): ByteArray {
        val b = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, s ->
            b[2 * i] = (s and 0xFF).toByte()
            b[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return b
    }

    private fun sine(amplitude: Int, n: Int = 1600) =
        pcm(List(n) { (amplitude * sin(2 * PI * it * 440 / 16000)).roundToInt() })

    @Test fun silenceIsFloor() =
        assertEquals(Loudness.FLOOR_DB, Loudness.frameDb(ByteArray(3200)), 0f)

    @Test fun emptyIsFloor() =
        assertEquals(Loudness.FLOOR_DB, Loudness.frameDb(ByteArray(0)), 0f)

    @Test fun fullScaleSquareIsAboutZeroDb() {
        val square = pcm(List(1600) { if (it % 2 == 0) 32767 else -32768 })
        assertEquals(0f, Loudness.frameDb(square), 0.01f)
    }

    // 振幅 A のサイン波の RMS は A/√2。フルスケールの半分(16384)なら -6.02 - 3.01 ≒ -9.03 dBFS
    @Test fun halfScaleSine() =
        assertEquals(-9.03f, Loudness.frameDb(sine(16384)), 0.05f)

    @Test fun halvingAmplitudeLowersBySixDb() {
        val diff = Loudness.frameDb(sine(16384)) - Loudness.frameDb(sine(8192))
        assertEquals(6.02f, diff, 0.05f)
    }

    @Test fun negativeSamplesAreHandled() =
        assertEquals(Loudness.frameDb(pcm(List(100) { 1000 })), Loudness.frameDb(pcm(List(100) { -1000 })), 0.001f)

    @Test fun aggregatorEmitsEveryTenFrames() {
        val agg = Loudness.SecondAggregator()
        repeat(9) { assertNull(agg.add(-50f)) }
        val s = agg.add(-50f)
        assertNotNull(s)
        assertEquals(0, s!!.second)
    }

    @Test fun aggregatorAverageAndMax() {
        val agg = Loudness.SecondAggregator(framesPerSecond = 4)
        listOf(-60f, -40f, -20f).forEach { assertNull(agg.add(it)) }
        val s = agg.add(-40f)!!
        assertEquals(-40f, s.avgDb, 0.001f)
        assertEquals(-20f, s.maxDb, 0.001f)
    }

    @Test fun aggregatorResetsAndIncrementsSecond() {
        val agg = Loudness.SecondAggregator(framesPerSecond = 2)
        agg.add(-10f); agg.add(-10f)
        agg.add(-80f)
        val s = agg.add(-80f)!!
        assertEquals(1, s.second)
        assertEquals(-80f, s.maxDb, 0.001f)
    }
}
