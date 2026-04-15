package io.github.keiai0.sleeprec

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioWindowsTest {
    @Test fun shortAudioIsOneWindow() = assertEquals(listOf(0), AudioWindows.starts(50, window = 100, hop = 50))

    @Test fun exactWindowIsOneWindow() = assertEquals(listOf(0), AudioWindows.starts(100, window = 100, hop = 50))

    @Test fun windowsCoverTheTail() {
        // 総量 260、窓 100、ずらし 50: 0, 50, 100, 150 の後、末尾にそろえた 160
        assertEquals(listOf(0, 50, 100, 150, 160), AudioWindows.starts(260, window = 100, hop = 50))
    }

    @Test fun tailWindowNotDuplicatedWhenAligned() =
        assertEquals(listOf(0, 50, 100), AudioWindows.starts(200, window = 100, hop = 50))

    @Test fun toFloatsConvertsAndPads() {
        // サンプル: 16384 (0x4000), -32768 (0x8000)
        val pcm = byteArrayOf(0x00, 0x40, 0x00, 0x80.toByte())
        val f = AudioWindows.toFloats(pcm, start = 0, count = 3)
        assertEquals(0.5f, f[0], 0f)
        assertEquals(-1f, f[1], 0f)
        assertEquals(0f, f[2], 0f) // 範囲外は 0
    }

    @Test fun maxPerClassTakesMaximum() {
        val m = AudioWindows.maxPerClass(listOf(floatArrayOf(0.1f, 0.9f, 0.2f), floatArrayOf(0.5f, 0.3f, 0.2f)))
        assertArrayEquals(floatArrayOf(0.5f, 0.9f, 0.2f), m, 0f)
    }

    @Test fun topKSortsDescending() {
        val top = AudioWindows.topK(floatArrayOf(0.1f, 0.7f, 0.3f, 0.5f), 2)
        assertEquals(listOf(1, 3), top.map { it.first })
    }
}
