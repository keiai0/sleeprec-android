package io.github.keiai0.sleeprec

import io.github.keiai0.sleeprec.data.AudioEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipPlaybackTest {
    private val start = 1_000_000L
    private fun ev(id: Long, offsetMs: Long, durationMs: Long = 2000, clip: String? = "x.wav") =
        AudioEvent(id = id, sessionId = 1, startedAt = start + offsetMs, durationMs = durationMs, maxDb = -20f, avgDb = -30f, clipPath = clip)

    @Test fun quietClipIsBoosted() = assertEquals(1000, ClipPlayback.gainMillibels(-22f))

    @Test fun boostIsCapped() = assertEquals(1200, ClipPlayback.gainMillibels(-60f))

    @Test fun loudClipIsNotChanged() = assertEquals(0, ClipPlayback.gainMillibels(-5f))

    @Test fun picksClosestByCenter() {
        val events = listOf(ev(1, 0), ev(2, 60_000), ev(3, 120_000))
        assertEquals(2L, ClipPlayback.nearestPlayable(events, start, 70_000)?.id)
        assertEquals(1L, ClipPlayback.nearestPlayable(events, start, 10_000)?.id)
        assertEquals(3L, ClipPlayback.nearestPlayable(events, start, 500_000)?.id)
    }

    @Test fun skipsClipsWithoutAudio() {
        val events = listOf(ev(1, 0), ev(2, 60_000, clip = null))
        assertEquals(1L, ClipPlayback.nearestPlayable(events, start, 60_000)?.id)
    }

    @Test fun nullWhenNothingPlayable() =
        assertNull(ClipPlayback.nearestPlayable(listOf(ev(1, 0, clip = null)), start, 0))

    @Test fun nullWhenEmpty() = assertNull(ClipPlayback.nearestPlayable(emptyList(), start, 0))
}

class PlayTargetTest {
    private val start = 1_000_000L
    private fun ev(id: Long, offsetMs: Long, durationMs: Long = 2000, clip: String? = "e.wav") =
        io.github.keiai0.sleeprec.data.AudioEvent(id = id, sessionId = 1, startedAt = start + offsetMs, durationMs = durationMs, maxDb = -20f, avgDb = -30f, clipPath = clip)
    private fun ap(id: Long, offsetMs: Long, silenceMs: Long = 20_000, clip: String? = "a.wav") =
        io.github.keiai0.sleeprec.data.ApneaCandidate(id = id, sessionId = 1, startedAt = start + offsetMs, silenceMs = silenceMs, maxDb = -25f, clipPath = clip)

    @Test fun combinesEventsAndApneasAndSkipsDeleted() {
        val t = ClipPlayback.targets(listOf(ev(1, 0), ev(2, 10_000, clip = null)), listOf(ap(1, 60_000), ap(2, 90_000, clip = null)), start)
        assertEquals(listOf(1L, -1L), t.map { it.id })
    }

    @Test fun apneaIdsAreNegativeSoTheyNeverCollideWithEvents() {
        val t = ClipPlayback.targets(listOf(ev(5, 0)), listOf(ap(5, 60_000)), start)
        assertEquals(setOf(5L, -5L), t.map { it.id }.toSet())
    }

    @Test fun nearestPicksAcrossKinds() {
        val t = ClipPlayback.targets(listOf(ev(1, 0), ev(2, 200_000)), listOf(ap(1, 100_000)), start)
        assertEquals(-1L, ClipPlayback.nearestTarget(t, 110_000)?.id) // 無呼吸の候補(中心 110 秒)に一番近い
        assertEquals(2L, ClipPlayback.nearestTarget(t, 190_000)?.id)
        assertEquals(1L, ClipPlayback.nearestTarget(t, 1_000)?.id)
    }

    @Test fun nearestIsNullWhenEmpty() = org.junit.Assert.assertNull(ClipPlayback.nearestTarget(emptyList(), 0))

    @Test fun apneaStartIsBeforeTheSilence() {
        val t = ClipPlayback.targets(emptyList(), listOf(ap(1, 60_000)), start).single()
        assertEquals(57_000L, t.startMs)
    }
}
