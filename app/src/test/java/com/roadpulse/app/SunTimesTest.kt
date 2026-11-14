package com.roadpulse.app

import com.roadpulse.app.nav.SunTimes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SunTimesTest {
    private val kl = ZoneId.of("Asia/Kuala_Lumpur")
    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 11, 14, h, m, 0, 0, kl).toInstant().toEpochMilli()
    private fun night(h: Int, m: Int) = SunTimes.isNight(3.139, 101.6869, at(h, m))

    @Test
    fun kualaLumpurDayAndNight() {
        // Mid-November in KL: sunrise about 07:00, sunset about 19:05.
        assertFalse(night(12, 0))
        assertFalse(night(8, 0))
        assertFalse(night(18, 30))
        assertTrue(night(19, 45))
        assertTrue(night(23, 0))
        assertTrue(night(5, 30))
    }

    @Test
    fun noonElevationIsHighInTheTropics() {
        assertTrue(SunTimes.elevationDeg(3.139, 101.6869, at(13, 5)) > 60)
    }
}
