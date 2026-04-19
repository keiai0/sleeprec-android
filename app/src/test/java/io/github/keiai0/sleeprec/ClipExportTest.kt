package io.github.keiai0.sleeprec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class ClipExportTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test fun fileNameHasDateTimeAndLabel() =
        assertEquals("SleepRec_20260919_152402_snoring.wav", ClipExport.fileName(1_789_831_442_000L, "snoring", utc))

    @Test fun fileNameFollowsTheTimeZone() {
        val tokyo = TimeZone.getTimeZone("Asia/Tokyo")
        assertEquals("SleepRec_20260920_002402_apnea.wav", ClipExport.fileName(1_789_831_442_000L, "apnea", tokyo))
    }

    @Test fun fileNameIsAscii() = assertTrue(ClipExport.fileName(0L, "snoring", utc).all { it.code < 128 })
}
