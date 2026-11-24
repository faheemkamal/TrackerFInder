package com.example.trackerfinder

import com.example.trackerfinder.findhub.HubPoint
import com.example.trackerfinder.findhub.Trips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureTest {
    private fun p(ts: Long, lat: Double, lon: Double) = HubPoint(ts, lat, lon, 20.0, false, null)

    @Test fun staysSplitHomeTripOffice() {
        val pts = listOf(
            p(0, 23.8100, 90.4100), p(900, 23.8101, 90.4101), p(1800, 23.8100, 90.4100),   // home 30 min
            p(2100, 23.8200, 90.4200),                                                     // passing through
            p(2400, 23.8300, 90.4300), p(4200, 23.8301, 90.4300), p(6000, 23.8300, 90.4301), // office 1 h
        )
        val s = Trips.stays(pts)
        assertEquals(2, s.size)
        assertEquals(0L, s[0].startTs); assertEquals(1800L, s[0].endTs); assertEquals(3, s[0].points)
        assertEquals(2400L, s[1].startTs); assertEquals(3600L, s[1].durationS); assertEquals(4, s[1].firstIndex)
    }

    @Test fun quietHoursWrapPastMidnight() {
        assertTrue(AlertRules.isWithin(23 * 60 + 30, 23 * 60, 7 * 60))
        assertTrue(AlertRules.isWithin(3 * 60, 23 * 60, 7 * 60))
        assertFalse(AlertRules.isWithin(7 * 60, 23 * 60, 7 * 60))
        assertFalse(AlertRules.isWithin(12 * 60, 23 * 60, 7 * 60))
        assertTrue(AlertRules.isWithin(13 * 60, 12 * 60, 14 * 60))
        assertFalse(AlertRules.isWithin(5, 0, 0))
    }
}
