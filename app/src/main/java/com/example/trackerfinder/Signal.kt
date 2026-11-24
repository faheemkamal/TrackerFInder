package com.example.trackerfinder

import kotlin.math.pow

/** Median-of-5 to drop spikes, then an exponential moving average to steady the number. */
class SignalFilter(private val windowSize: Int = 5, private val alpha: Double = 0.3) {
    private val window = ArrayDeque<Int>()
    var smoothed: Double? = null
        private set

    fun add(rssi: Int): Double {
        window.addLast(rssi)
        if (window.size > windowSize) window.removeFirst()
        val median = window.sorted()[window.size / 2].toDouble()
        val next = smoothed?.let { it + alpha * (median - it) } ?: median
        smoothed = next
        return next
    }

    fun reset() {
        window.clear()
        smoothed = null
    }
}

enum class Zone(val label: String, val hint: String) {
    HERE("Right here", "Within arm's reach"),
    VERY_CLOSE("Very close", "About a meter away"),
    NEAR("Near", "A few meters away"),
    AREA("In the area", "Probably this room or the next"),
    FAR("Far", "Weak signal, keep moving around"),
}

enum class Trend(val label: String) {
    WARMER("Getting warmer ▲"),
    COLDER("Getting colder ▼"),
    STEADY("Steady"),
}

object Proximity {
    /** Default signal at 1 m. GUESS for a small coin-cell tracker; use Calibrate to fix it. */
    const val DEFAULT_RSSI_AT_1M = -60

    /** Path-loss exponent: 2 in open air, 2.5 to 3.5 indoors. */
    const val PATH_LOSS_N = 2.7

    fun zone(smoothed: Double, rssiAt1m: Int): Zone {
        val d = smoothed - rssiAt1m
        return when {
            d >= 8 -> Zone.HERE
            d >= -4 -> Zone.VERY_CLOSE
            d >= -14 -> Zone.NEAR
            d >= -24 -> Zone.AREA
            else -> Zone.FAR
        }
    }

    /** Log-distance model. Very rough indoors: walls, bodies and pockets swing it a lot. */
    fun estimateMeters(smoothed: Double, rssiAt1m: Int, n: Double = PATH_LOSS_N): Double =
        10.0.pow((rssiAt1m - smoothed) / (10 * n))

    /** 0 = cold (-100 dBm), 1 = hot (-40 dBm). */
    fun heat(smoothed: Double): Float = ((smoothed + 100.0) / 60.0).toFloat().coerceIn(0f, 1f)

    /** Compares the last second against three seconds earlier (samples are 0.5 s apart). */
    fun trend(history: List<Float>): Trend {
        if (history.size < 8) return Trend.STEADY
        val now = history.takeLast(2).average()
        val before = history.subList(history.size - 8, history.size - 6).average()
        return when {
            now - before >= 2.5 -> Trend.WARMER
            now - before <= -2.5 -> Trend.COLDER
            else -> Trend.STEADY
        }
    }
}
