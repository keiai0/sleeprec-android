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
