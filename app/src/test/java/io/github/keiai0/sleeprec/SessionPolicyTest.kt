package io.github.keiai0.sleeprec

import io.github.keiai0.sleeprec.SessionPolicy.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionPolicyTest {
    private fun min(m: Long) = m * 60_000L

    @Test fun zeroIsNotSaved() = assertEquals(Outcome.NOT_SAVED, SessionPolicy.classify(0))

    @Test fun justUnder10MinutesIsNotSaved() =
        assertEquals(Outcome.NOT_SAVED, SessionPolicy.classify(min(10) - 1))

    @Test fun exactly10MinutesIsShortSleep() =
        assertEquals(Outcome.SHORT_SLEEP, SessionPolicy.classify(min(10)))

    @Test fun justUnder30MinutesIsShortSleep() =
        assertEquals(Outcome.SHORT_SLEEP, SessionPolicy.classify(min(30) - 1))

    @Test fun exactly30MinutesIsNormal() =
        assertEquals(Outcome.NORMAL, SessionPolicy.classify(min(30)))

    @Test fun eightHoursIsNormal() =
        assertEquals(Outcome.NORMAL, SessionPolicy.classify(min(8 * 60)))
}
