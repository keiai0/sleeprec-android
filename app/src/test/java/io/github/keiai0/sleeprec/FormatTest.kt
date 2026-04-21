package io.github.keiai0.sleeprec

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun seconds() = assertEquals("34 秒", formatDurationJa(34_000))
    @Test fun zero() = assertEquals("0 秒", formatDurationJa(0))
    @Test fun minutesAndSeconds() = assertEquals("1 分 19 秒", formatDurationJa(79_000))
    @Test fun justUnderAnHour() = assertEquals("59 分 59 秒", formatDurationJa(3_599_000))
    @Test fun hoursAndMinutesDropSeconds() = assertEquals("7 時間 30 分", formatDurationJa((7 * 3600 + 30 * 60 + 45) * 1000L))
    @Test fun negativeIsZero() = assertEquals("0 秒", formatDurationJa(-5_000))
}
