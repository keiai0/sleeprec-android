package io.github.keiai0.sleeprec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRulesTest {
    @Test fun goalIsClampedToRange() {
        assertEquals(240, SettingsRules.clampGoal(0))
        assertEquals(240, SettingsRules.clampGoal(239))
        assertEquals(720, SettingsRules.clampGoal(9999))
        assertEquals(450, SettingsRules.clampGoal(450))
    }

    @Test fun goalIsRoundedToTheStep() {
        assertEquals(450, SettingsRules.clampGoal(455))
        assertEquals(465, SettingsRules.clampGoal(460))
        assertEquals(0, SettingsRules.clampGoal(437) % SettingsRules.GOAL_STEP)
    }

    @Test fun goalStepsUpAndDownButStopsAtTheEnds() {
        assertEquals(465, SettingsRules.stepGoal(450, +1))
        assertEquals(435, SettingsRules.stepGoal(450, -1))
        assertEquals(720, SettingsRules.stepGoal(720, +1))
        assertEquals(240, SettingsRules.stepGoal(240, -1))
    }

    @Test fun cutoffWrapsAround() {
        assertEquals(16, SettingsRules.stepCutoff(15, +1))
        assertEquals(0, SettingsRules.stepCutoff(23, +1))
        assertEquals(23, SettingsRules.stepCutoff(0, -1))
    }

    @Test fun spanCrossesMidnight() {
        assertEquals(8 * 60, SettingsRules.sleepSpanMinutes(23 * 60, 7 * 60))
        assertEquals(7 * 60 + 30, SettingsRules.sleepSpanMinutes(0, 7 * 60 + 30))
        assertEquals(0, SettingsRules.sleepSpanMinutes(7 * 60, 7 * 60))
    }

    @Test fun spanMustBeOneToTwentyHours() {
        assertFalse(SettingsRules.isValidSpan(0))
        assertFalse(SettingsRules.isValidSpan(59))
        assertTrue(SettingsRules.isValidSpan(60))
        assertTrue(SettingsRules.isValidSpan(20 * 60))
        assertFalse(SettingsRules.isValidSpan(20 * 60 + 1))
    }

    @Test fun sameBedAndWakeTimeIsInvalid() =
        assertFalse(SettingsRules.isValidSpan(SettingsRules.sleepSpanMinutes(6 * 60, 6 * 60)))

    @Test fun twentyOneHoursApartIsInvalid() =
        // 23:00 → 20:00 は 21 時間
        assertFalse(SettingsRules.isValidSpan(SettingsRules.sleepSpanMinutes(23 * 60, 20 * 60)))

    @Test fun goalFromSpanClamps() {
        assertEquals(480, SettingsRules.goalFromSpan(480))
        assertEquals(240, SettingsRules.goalFromSpan(90))
        assertEquals(720, SettingsRules.goalFromSpan(15 * 60))
    }

    @Test fun birthDateValidation() {
        val today = 20_000L
        assertTrue(SettingsRules.isValidBirthDate(today - 365 * 30, today))
        assertTrue(SettingsRules.isValidBirthDate(today, today))
        assertFalse(SettingsRules.isValidBirthDate(today + 1, today))
        assertFalse(SettingsRules.isValidBirthDate(today - 365 * 130, today))
    }

    @Test fun unitConversionsRoundTrip() {
        assertEquals(170, SettingsRules.inchesToCm(SettingsRules.cmToInches(170)))
        assertEquals(68.9, SettingsRules.cmToInches(175), 0.05)
        assertEquals(60f, SettingsRules.poundsToKg(SettingsRules.kgToPounds(60f)), 0.001f)
        assertEquals(154.3, SettingsRules.kgToPounds(70f), 0.05)
    }
}
