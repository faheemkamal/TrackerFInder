package com.example.trackerfinder.findhub

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    /** Great-circle distance in meters. */
    fun distanceM(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val r = 6_371_000.0
        val p = Math.PI / 180
        val dLat = (bLat - aLat) * p
        val dLon = (bLon - aLon) * p
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(aLat * p) * cos(bLat * p) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(h))
    }
}

/** A place the tag stayed: centre, first and last time there (unix s), and how many reports. */
data class Stay(val lat: Double, val lon: Double, val startTs: Long, val endTs: Long, val points: Int, val firstIndex: Int) {
    val durationS: Long get() = endTs - startTs
}

data class TripStats(
    val points: Int,
    val distanceM: Double,
    val durationS: Long,
    val movingS: Long,
    val maxGapS: Long,
    val avgSpeedKmh: Double?,
)

object Trips {
    /**
     * Distance and time for a set of points (time-ordered). Jumps shorter than [jitterM] are ignored,
     * because Find Hub positions wobble by tens of meters even when the tag doesn't move.
     */
    fun stats(points: List<HubPoint>, jitterM: Double = 40.0): TripStats {
        val geo = points.filter { it.lat != null && it.lon != null }
        if (geo.isEmpty()) return TripStats(points.size, 0.0, 0, 0, 0, null)
        var dist = 0.0
        var moving = 0L
        var maxGap = 0L
        for (i in 1 until geo.size) {
            val a = geo[i - 1]
            val b = geo[i]
            val d = Geo.distanceM(a.lat!!, a.lon!!, b.lat!!, b.lon!!)
            val dt = b.ts - a.ts
            maxGap = maxOf(maxGap, dt)
            if (d >= jitterM) {
                dist += d
                moving += dt
            }
        }
        val duration = geo.last().ts - geo.first().ts
        val avg = if (moving > 60) dist / 1000.0 / (moving / 3600.0) else null
        return TripStats(geo.size, dist, duration, moving, maxGap, avg)
    }

    /**
     * Timeline of places visited: consecutive reports within [radiusM] of their running centre form
     * a stay; stays shorter than [minStayS] are treated as passing through. [firstIndex] is the
     * index of the stay's first point in the coordinate-only list (for replay).
     */
    fun stays(points: List<HubPoint>, radiusM: Double = 120.0, minStayS: Long = 10 * 60): List<Stay> {
        val geo = points.filter { it.lat != null && it.lon != null }
        val out = ArrayList<Stay>()
        var i = 0
        while (i < geo.size) {
            var lat = geo[i].lat!!
            var lon = geo[i].lon!!
            var n = 1
            var j = i + 1
            while (j < geo.size && Geo.distanceM(lat, lon, geo[j].lat!!, geo[j].lon!!) <= radiusM) {
                lat = (lat * n + geo[j].lat!!) / (n + 1)
                lon = (lon * n + geo[j].lon!!) / (n + 1)
                n++; j++
            }
            val start = geo[i].ts
            val end = geo[j - 1].ts
            if (end - start >= minStayS) out += Stay(lat, lon, start, end, n, i)
            i = j
        }
        return out
    }
}
