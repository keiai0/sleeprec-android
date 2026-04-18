package io.github.keiai0.sleeprec

import io.github.keiai0.sleeprec.PreflightRules.Battery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreflightRulesTest {
    @Test fun fiftyPercentOrMoreIsOk() {
        assertEquals(Battery.OK, PreflightRules.battery(50, false))
        assertEquals(Battery.OK, PreflightRules.battery(100, false))
    }

    @Test fun below50WarnsWhenNotCharging() {
        assertEquals(Battery.LOW, PreflightRules.battery(49, false))
        assertEquals(Battery.LOW, PreflightRules.battery(30, false))
    }

    @Test fun below30IsVeryLow() {
        assertEquals(Battery.VERY_LOW, PreflightRules.battery(29, false))
        assertEquals(Battery.VERY_LOW, PreflightRules.battery(0, false))
    }

    @Test fun chargingIsFineAtAnyLevel() {
        assertEquals(Battery.OK_CHARGING, PreflightRules.battery(5, true))
        assertEquals(Battery.OK_CHARGING, PreflightRules.battery(80, true))
    }

    @Test fun warningFlags() {
        assertTrue(PreflightRules.batteryIsWarning(Battery.LOW))
        assertTrue(PreflightRules.batteryIsWarning(Battery.VERY_LOW))
        assertFalse(PreflightRules.batteryIsWarning(Battery.OK))
        assertFalse(PreflightRules.batteryIsWarning(Battery.OK_CHARGING))
    }
}
