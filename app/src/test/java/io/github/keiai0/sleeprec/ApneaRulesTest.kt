package io.github.keiai0.sleeprec

import io.github.keiai0.sleeprec.ApneaRules.Mark
import io.github.keiai0.sleeprec.data.EventType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApneaRulesTest {
    private fun scores(vararg pairs: Pair<Int, Float>) = FloatArray(521).also { for ((i, v) in pairs) it[i] = v }
    private val snore = Mark(EventType.SNORING, 0, 20_000)
    private fun next(startMs: Long, type: EventType = EventType.SNORING) = Mark(type, startMs, startMs + 5_000)
    private val snoreScores = scores(38 to 0.8f)

    @Test fun tenSecondSilenceAfterSnoreIsCandidate() =
        assertEquals(10_000L, ApneaRules.silenceMs(snore, next(30_000), snoreScores))

    @Test fun shorterThanMinIsNot() = assertNull(ApneaRules.silenceMs(snore, next(29_999), snoreScores))

    @Test fun longerThanMaxIsNot() = assertNull(ApneaRules.silenceMs(snore, next(20_000 + 90_001), snoreScores))

    @Test fun exactlyMaxIsCandidate() =
        assertEquals(90_000L, ApneaRules.silenceMs(snore, next(110_000), snoreScores))

    @Test fun prevMustBeSnoring() =
        assertNull(ApneaRules.silenceMs(Mark(EventType.COUGH, 0, 20_000), next(40_000), snoreScores))

    @Test fun noPrevIsNot() = assertNull(ApneaRules.silenceMs(null, next(40_000), snoreScores))

    @Test fun resumeWithGaspOrSnort() {
        assertEquals(15_000L, ApneaRules.silenceMs(snore, next(35_000, EventType.OTHER), scores(39 to 0.5f)))
        assertEquals(15_000L, ApneaRules.silenceMs(snore, next(35_000, EventType.OTHER), scores(41 to 0.5f)))
    }

    @Test fun resumeWithSpeechOnlyIsNot() =
        assertNull(ApneaRules.silenceMs(snore, next(35_000, EventType.SLEEP_TALK), scores(0 to 0.9f)))

    @Test fun resumesNeedsMinScore() = assertFalse(ApneaRules.resumes(scores(39 to 0.29f)))

    @Test fun resumesTrueAtMinScore() = assertTrue(ApneaRules.resumes(scores(36 to 0.3f)))

    // --- クリップの組み立て(1 ミリ秒 = 32 バイト) ---
    private fun pcm(ms: Int, v: Int) = ByteArray(ms * 32) { v.toByte() }

    @Test fun clipIsTailPlusGapPlusHead() {
        val prev = pcm(10_000, 1)   // 前のクリップ(10 秒)。末尾 5 秒を使う
        val lead = pcm(30_000, 2)   // 後のクリップの直前 30 秒
        val nxt = pcm(20_000, 3)    // 後のクリップ(20 秒)。先頭 8 秒を使う
        // 前のクリップの終わりが 100 秒、後のクリップの始まりが 112 秒 → 間は 12 秒
        val clip = ApneaRules.buildClip(prev, prevEndMs = 100_000, nextPcm = nxt, nextStartMs = 112_000, lead = lead)
        assertEquals((5_000 + 12_000 + 8_000) * 32, clip.size)
        assertEquals(1, clip[0].toInt())
        assertEquals(2, clip[5_000 * 32].toInt())
        assertEquals(3, clip[(5_000 + 12_000) * 32].toInt())
    }

    @Test fun gapLongerThanLeadUsesWhatExists() {
        val clip = ApneaRules.buildClip(pcm(2_000, 1), 0, pcm(2_000, 3), nextStartMs = 60_000, lead = pcm(30_000, 2))
        // tail 2 秒(あるだけ) + lead 30 秒 + head 2 秒(あるだけ)
        assertEquals((2_000 + 30_000 + 2_000) * 32, clip.size)
    }

    @Test fun contiguousClipsHaveNoGap() {
        val clip = ApneaRules.buildClip(pcm(6_000, 1), 6_000, pcm(6_000, 3), nextStartMs = 6_000, lead = pcm(30_000, 2))
        assertEquals((5_000 + 6_000) * 32, clip.size)
    }

    @Test fun clipStaysSampleAligned() {
        val clip = ApneaRules.buildClip(ByteArray(101), 0, ByteArray(103), 1, ByteArray(9))
        assertEquals(0, clip.size % 2)
    }

    @Test fun emptyLeadGivesShortGap() {
        val clip = ApneaRules.buildClip(pcm(5_000, 1), 5_000, pcm(8_000, 3), 25_000, ByteArray(0))
        assertArrayEquals(ByteArray(13_000 * 32).also {
            for (i in 0 until 5_000 * 32) it[i] = 1
            for (i in 5_000 * 32 until it.size) it[i] = 3
        }, clip)
    }
}
