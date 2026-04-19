package io.github.keiai0.sleeprec

import io.github.keiai0.sleeprec.data.EventType
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkerKindTest {
    @Test fun snoringTalkCoughHaveOwnMarkers() {
        assertEquals(MarkerKind.SNORE, MarkerKind.of(EventType.SNORING))
        assertEquals(MarkerKind.TALK, MarkerKind.of(EventType.SLEEP_TALK))
        assertEquals(MarkerKind.COUGH, MarkerKind.of(EventType.COUGH))
    }

    @Test fun everythingElseIsOther() {
        for (t in listOf(EventType.FART, EventType.FOOTSTEPS, EventType.ANIMAL, EventType.GRINDING, EventType.AMBIENT, EventType.OTHER, EventType.UNCLASSIFIED)) {
            assertEquals("$t", MarkerKind.OTHER, MarkerKind.of(t))
        }
    }

    @Test fun everyEventTypeMapsToSomeMarker() {
        for (t in EventType.entries) MarkerKind.of(t) // 例外なく決まる(新しい種別を足したときの取りこぼし防止)
    }
}
