package com.example.trackerfinder

import com.example.trackerfinder.findhub.Geo
import com.example.trackerfinder.findhub.HubPoint
import com.example.trackerfinder.findhub.Trips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripTest {
    private fun p(ts: Long, lat: Double?, lon: Double?) = HubPoint(ts, lat, lon, 20.0, false, null)

    @Test fun haversineOneDegreeLatitude() {
        assertEquals(111_195.0, Geo.distanceM(0.0, 0.0, 1.0, 0.0), 50.0)
    }

    @Test fun statsIgnoreJitterAndSemanticPoints() {
        // ~0.001° lat ≈ 111 m per step; a 10 m wobble in between is ignored.
        val pts = listOf(
            p(0, 23.8100, 90.4100),
            p(600, 23.81009, 90.4100),     // ~10 m jitter
            p(1200, 23.8110, 90.4100),     // ~101 m move
            p(1500, null, null),           // semantic report, no coordinates
            p(1800, 23.8120, 90.4100),     // ~111 m move
        )
        val s = Trips.stats(pts)
        assertEquals(4, s.points)
        assertEquals(212.0, s.distanceM, 5.0)
        assertEquals(1800L, s.durationS)
        assertEquals(1200L, s.movingS)
    }

    @Test fun emptyStats() {
        val s = Trips.stats(emptyList())
        assertEquals(0.0, s.distanceM, 0.0)
        assertNull(s.avgSpeedKmh)
    }
}
