package io.github.keiai0.sleeprec

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WaveformTest {
    private fun pcm(samples: List<Int>): ByteArray {
        val b = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, s ->
            b[2 * i] = (s and 0xFF).toByte()
            b[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return b
    }

    @Test fun silenceIsZero() = assertArrayEquals(FloatArray(4), Waveform.envelope(ByteArray(800), 4), 0f)

    @Test fun emptyGivesZeros() = assertArrayEquals(FloatArray(3), Waveform.envelope(ByteArray(0), 3), 0f)

    @Test fun bucketCountIsExact() = assertEquals(50, Waveform.envelope(ByteArray(10_000), 50).size)

    @Test fun peakGoesToTheRightBucket() {
        // 100 サンプル、10 区間。40 番目のサンプル(区間 4)だけが大きい
        val samples = MutableList(100) { 0 }
        samples[40] = 16384
        val env = Waveform.envelope(pcm(samples), 10)
        assertEquals(0.5f, env[4], 1e-6f)
        for (i in listOf(0, 1, 2, 3, 5, 6, 7, 8, 9)) assertEquals(0f, env[i], 0f)
    }

    @Test fun negativeSamplesCountByMagnitude() {
        val env = Waveform.envelope(pcm(listOf(0, -32768, 0, 0)), 1)
        assertEquals(1f, env[0], 0f)
    }

    @Test fun fewerSamplesThanBucketsStillWorks() {
        val env = Waveform.envelope(pcm(listOf(8192, 16384)), 8)
        assertEquals(8, env.size)
        assertEquals(0.5f, env.max(), 1e-6f)
    }

    @Test fun normalizeScalesMaxToOne() {
        val n = Waveform.normalize(floatArrayOf(0.1f, 0.05f, 0f))
        assertEquals(1f, n[0], 1e-6f)
        assertEquals(0.5f, n[1], 1e-6f)
    }

    @Test fun normalizeKeepsSilence() = assertArrayEquals(floatArrayOf(0f, 0f), Waveform.normalize(floatArrayOf(0f, 0f)), 0f)
}
