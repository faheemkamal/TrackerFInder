package com.example.trackerfinder

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One Wi-Fi access point heard by the phone. [rssi] is the real dBm Android reports. */
data class WifiHeard(val bssid: String, val ssid: String?, val rssi: Int, val ageMs: Long = 0)

/**
 * Wi-Fi "tracking": lists nearby Wi-Fi networks (access points, hotspots, Wi-Fi cameras...) by
 * signal, so one can be found with the same hot/cold finder as a Bluetooth tag.
 *
 * Android limits apps to 4 Wi-Fi scans every 2 minutes, so a full scan is asked for every 30 s.
 * Results that other apps or the system trigger arrive too, for free. Two ways to go faster:
 * - The network the phone is connected to reports its signal every second, without scanning.
 * - Developer options > "Wi-Fi scan throttling" off removes the 4-per-2-minutes limit; then we
 *   scan every 5 s.
 */
class WifiScan(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val onScan: (List<WifiHeard>) -> Unit,
    private val onConnected: (WifiHeard) -> Unit,
) {
    private val wm = ctx.applicationContext.getSystemService(WifiManager::class.java)
    private var job: Job? = null
    private var registered = false
    @Volatile private var lastResultsAt = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { deliver() }
    }

    val running get() = job?.isActive == true

    fun start() {
        if (running || wm == null || !allowed()) return
        if (!registered) {
            ContextCompat.registerReceiver(ctx, receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), ContextCompat.RECEIVER_EXPORTED)
            registered = true
        }
        deliver() // whatever the phone already knows, right away
        job = scope.launch {
            var lastScan = 0L
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                val every = if (throttled()) 30_000L else 5_000L
                if (now - lastScan >= every) {
                    @Suppress("DEPRECATION") runCatching { wm.startScan() }
                    lastScan = now
                }
                connected()?.let(onConnected)
                delay(1_000)
            }
        }
    }

    fun stop() {
        job?.cancel(); job = null
        if (registered) { runCatching { ctx.unregisterReceiver(receiver) }; registered = false }
    }

    /** True unless the user turned off scan throttling in Developer options (Android 11+ can tell). */
    fun throttled(): Boolean = if (Build.VERSION.SDK_INT >= 30) runCatching { wm?.isScanThrottleEnabled ?: true }.getOrDefault(true) else true

    fun isWifiOn(): Boolean = wm?.isWifiEnabled == true

    private fun allowed() = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun deliver() {
        if (!allowed()) return
        val nowUs = SystemClock.elapsedRealtimeNanos() / 1000
        val list = runCatching { wm?.scanResults }.getOrNull() ?: return
        lastResultsAt = SystemClock.elapsedRealtime()
        onScan(
            list.filter { nowUs - it.timestamp < 90_000_000L } // ignore results older than 90 s
                .map { WifiHeard(it.BSSID.uppercase(), ssidOf(it), it.level, ((nowUs - it.timestamp) / 1000).coerceAtLeast(0)) },
        )
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun connected(): WifiHeard? {
        val info = runCatching { wm?.connectionInfo }.getOrNull() ?: return null
        val bssid = info.bssid ?: return null
        if (bssid == "02:00:00:00:00:00" || info.rssi <= -127) return null
        val ssid = info.ssid?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        return WifiHeard(bssid.uppercase(), ssid, info.rssi)
    }

    private fun ssidOf(r: android.net.wifi.ScanResult): String? {
        val s = if (Build.VERSION.SDK_INT >= 33) r.wifiSsid?.toString()?.removeSurrounding("\"") else @Suppress("DEPRECATION") r.SSID
        return s?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }
}
