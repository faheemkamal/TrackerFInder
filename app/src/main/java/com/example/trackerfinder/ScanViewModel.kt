package com.example.trackerfinder

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

enum class ScanMode { OFF, BALANCED, FAST }

data class SeenDevice(
    val address: String,
    val name: String?,
    val kind: TrackerKind,
    val fingerprint: String,
    val rssi: Int,
    val firstSeen: Long,
    val lastSeen: Long,
)

data class TrackState(
    /** Empty while we're still searching for a favorite that isn't in range yet. */
    val address: String,
    val kind: TrackerKind,
    val fingerprint: String,
    val name: String?,
    val rawRssi: Int?,
    val smoothed: Double?,
    val lastSeen: Long,
    val history: List<Float>,
    val trend: Trend,
    val addressChanges: Int,
    /** Other visible trackers that look exactly like this one (same type signature). */
    val lookalikes: Int = 0,
    val favoriteId: String? = null,
    /** Counts adverts heard from the target; lets the UI react to every reading. */
    val samples: Int = 0,
) {
    val searching: Boolean get() = address.isEmpty()
}

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val adapter = (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val _devices = MutableStateFlow<Map<String, SeenDevice>>(emptyMap())
    val devices: StateFlow<Map<String, SeenDevice>> = _devices.asStateFlow()

    private val _track = MutableStateFlow<TrackState?>(null)
    val track: StateFlow<TrackState?> = _track.asStateFlow()

    private val _favorites = MutableStateFlow(FavoritesStore.load(prefs))
    val favorites: StateFlow<List<Favorite>> = _favorites.asStateFlow()
    private var favoritesDirty = false

    private val _rssiAt1m = MutableStateFlow(prefs.getInt("rssiAt1m", Proximity.DEFAULT_RSSI_AT_1M))
    val rssiAt1m: StateFlow<Int> = _rssiAt1m.asStateFlow()

    private val _beep = MutableStateFlow(prefs.getBoolean("beep", true))
    val beep: StateFlow<Boolean> = _beep.asStateFlow()

    private val _vibrate = MutableStateFlow(prefs.getBoolean("vibrate", false))
    val vibrate: StateFlow<Boolean> = _vibrate.asStateFlow()

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Live map updated on every advert; published to [devices] at most twice a second to spare the UI. */
    private val live = LinkedHashMap<String, SeenDevice>()
    private val filter = SignalFilter()
    /** How hard to scan; picked by the screen that's showing (see [setScanMode]). */
    private var mode = ScanMode.BALANCED
    /** True between the activity's onStart and onStop. */
    private var active = false
    private val recentStarts = ArrayDeque<Long>()
    private var pendingRestart: Job? = null
    private var scanning = false
    private var scanStartedAt = 0L
    private var tickJob: Job? = null
    private var beepJob: Job? = null
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()


    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = onResult(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::onResult)
        override fun onScanFailed(errorCode: Int) {
            scanning = false
            _error.value = when (errorCode) {
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ->
                    "Android refused the scan (often from restarting too often). Wait 30 seconds and try again."
                else -> "Bluetooth scan failed (code $errorCode). Try turning Bluetooth off and on."
            }
        }
    }

    /** The app came to the foreground (MainActivity.onStart, after permissions). */
    fun start() {
        active = true
        applyScan()
    }

    /** The app left the foreground. */
    fun stop() {
        active = false
        pendingRestart?.cancel(); pendingRestart = null
        stopScanOnly()
        tickJob?.cancel(); tickJob = null
        beepJob?.cancel(); beepJob = null
        persistFavoritesIfDirty()
    }

    /**
     * Fast scanning only while you're actively hunting (finder or Nearby list); a gentler mode
     * for Home/Favorites status; none at all on Map and More. Saves a lot of battery.
     */
    /** Temporarily scan at full speed (e.g. while learning the tracker's button for Find my phone). */
    private var boost = false
    fun setBoost(on: Boolean) {
        if (on == boost) return
        boost = on
        if (active) applyScan()
    }

    fun setScanMode(m: ScanMode) {
        if (m == mode) return
        mode = m
        if (active) applyScan()
    }

    /** Bluetooth was switched on or off while the app is open. */
    fun onBluetoothChanged(on: Boolean) {
        if (!on) {
            stopScanOnly()
            _error.value = "Bluetooth is off. Turn it on to scan."
        } else if (active) {
            _error.value = null
            applyScan()
        }
    }

    @SuppressLint("MissingPermission") // MainActivity only calls start() after permissions are granted.
    private fun applyScan() {
        if (!active) return
        if (tickJob == null) tickJob = viewModelScope.launch { tickLoop() }
        if (beepJob == null) beepJob = viewModelScope.launch { beepLoop() }
        if (scanning) stopScanOnly()
        val mode = if (boost) ScanMode.FAST else mode
        if (mode == ScanMode.OFF) return
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null || adapter?.isEnabled != true) {
            _error.value = "Bluetooth is off. Turn it on to scan."
            return
        }
        // Android silently ignores a 6th scan start within 30 s; wait instead of losing the scan.
        val now = SystemClock.elapsedRealtime()
        while (recentStarts.isNotEmpty() && now - recentStarts.first() > 30_000) recentStarts.removeFirst()
        if (recentStarts.size >= 4) {
            if (pendingRestart?.isActive != true) pendingRestart = viewModelScope.launch {
                delay(30_000 - (now - recentStarts.first()) + 500)
                pendingRestart = null
                applyScan()
            }
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(if (mode == ScanMode.FAST) ScanSettings.SCAN_MODE_LOW_LATENCY else ScanSettings.SCAN_MODE_BALANCED)
            .setReportDelay(0)
            .build()
        try {
            scanner.startScan(null, settings, callback)
            recentStarts.addLast(now)
            scanning = true
            scanStartedAt = now
            _error.value = null
        } catch (e: SecurityException) {
            _error.value = "Bluetooth permission is missing."
        } catch (e: IllegalStateException) {
            _error.value = "Bluetooth is off. Turn it on to scan."
        }
    }

    private fun onResult(r: ScanResult) {
        val now = SystemClock.elapsedRealtime()
        val old = live[r.device.address]
        val seen = parseScan(r, old, now)
        val kind = seen.kind
        val name = seen.name
        val address = seen.address
        val fingerprint = seen.fingerprint

        live[address] = seen
        if (kind == TrackerKind.GOOGLE_FIND_HUB || kind == TrackerKind.GOOGLE_FIND_HUB_SEPARATED) {
            serviceData16(r)[AdvertParser.EDDYSTONE_UUID16]?.let { com.example.trackerfinder.findhub.BeaconBattery.onAdvert(getApplication(), address, it) }
        }
        FindPhone.onAdvert(getApplication(), r)
        // Presence feeds the widget and left-behind alerts.
        var presenceChanged = false
        _favorites.value.forEach { if (it.matches(seen)) presenceChanged = Presence.heard(getApplication(), it.id) || presenceChanged }
        if (presenceChanged) FavoritesWidget.updateAll(getApplication())
        // Keep "last seen" fresh for favorites we can hear by their exact address.
        // (Throttled: rewriting the list on every advert made every screen redraw constantly.)
        if (_favorites.value.any { it.lastAddress == address && System.currentTimeMillis() - it.lastSeenAt > 10_000 }) {
            touchFavorites({ it.lastAddress == address }, address)
        }

        val t = _track.value ?: return
        if (t.searching) return // the tick loop picks the best candidate
        if (address == t.address) {
            val s = filter.add(r.rssi)
            _track.value = t.copy(rawRssi = r.rssi, smoothed = s, lastSeen = now, kind = kind, samples = t.samples + 1)
            return
        }
        // Trackers change their Bluetooth address every ~15 minutes for privacy.
        // Follow-on rule (a guess, not a guarantee): if our target went quiet and a new device
        // with the same type signature appeared after it went quiet, assume it's the same tracker.
        val firstSeen = live[address]?.firstSeen ?: now
        val followable = t.kind.isTracker || t.name != null
        if (followable && fingerprint == t.fingerprint && now - t.lastSeen > LOST_AFTER_MS &&
            firstSeen >= t.lastSeen - 1_000
        ) {
            val s = filter.add(r.rssi)
            _track.value = t.copy(
                address = address, kind = kind, name = name ?: t.name, rawRssi = r.rssi,
                smoothed = s, lastSeen = now, addressChanges = t.addressChanges + 1, samples = t.samples + 1,
            )
            t.favoriteId?.let { id -> touchFavorites({ it.id == id }, address) }
        }
    }

    private fun touchFavorites(which: (Favorite) -> Boolean, address: String) {
        val wall = System.currentTimeMillis()
        _favorites.update { list ->
            list.map { if (which(it)) it.copy(lastAddress = address, lastSeenAt = wall) else it }
        }
        favoritesDirty = true
    }

    /** Opens the finder on a device picked from the Nearby list. */
    fun select(device: SeenDevice) {
        val fav = _favorites.value.firstOrNull { it.matchesExactly(device) }
        openOn(device, fav?.id)
    }

    private fun openOn(device: SeenDevice, favoriteId: String?) {
        filter.reset()
        val s = filter.add(device.rssi)
        _track.value = TrackState(
            address = device.address, kind = device.kind, fingerprint = device.fingerprint,
            name = device.name, rawRssi = device.rssi, smoothed = s, lastSeen = device.lastSeen,
            history = emptyList(), trend = Trend.STEADY, addressChanges = 0, favoriteId = favoriteId,
        )
        favoriteId?.let { id -> touchFavorites({ it.id == id }, device.address) }
    }

    /** Opens the finder for a favorite. If it isn't in range yet, the finder shows "searching". */
    fun find(favoriteId: String) {
        val fav = _favorites.value.firstOrNull { it.id == favoriteId } ?: return
        val justStarted = SystemClock.elapsedRealtime() - scanStartedAt < AUTO_OPEN_WAIT_MS
        val match = if (scanning && !justStarted) fav.bestMatch(_devices.value.values) else null
        if (match != null) {
            openOn(match, fav.id)
        } else {
            filter.reset()
            _track.value = TrackState(
                address = "", kind = fav.kind, fingerprint = fav.fingerprint, name = fav.name,
                rawRssi = null, smoothed = null, lastSeen = 0, history = emptyList(),
                trend = Trend.STEADY, addressChanges = 0, favoriteId = fav.id,
            )
        }
    }

    fun closeFinder() {
        _track.value = null
        filter.reset()
    }

    fun addFavorite(nickname: String) {
        val t = _track.value ?: return
        if (t.searching) return
        val fav = Favorite(
            nickname = nickname.trim().ifEmpty { "My tracker" }, fingerprint = t.fingerprint,
            kind = t.kind, name = t.name, lastAddress = t.address, lastSeenAt = System.currentTimeMillis(),
        )
        _favorites.update { it + fav }
        _track.value = t.copy(favoriteId = fav.id)
        persistFavorites()
    }

    fun renameFavorite(id: String, nickname: String) {
        val n = nickname.trim()
        if (n.isEmpty()) return
        _favorites.update { list -> list.map { if (it.id == id) it.copy(nickname = n) else it } }
        persistFavorites()
    }

    fun removeFavorite(id: String) {
        _favorites.update { list -> list.filterNot { it.id == id } }
        Presence.forget(getApplication(), id)
        _track.update { t -> if (t?.favoriteId == id) t.copy(favoriteId = null) else t }
        persistFavorites()
    }

    private fun persistFavorites() {
        FavoritesStore.save(prefs, _favorites.value)
        favoritesDirty = false
        FavoritesWidget.updateAll(getApplication())
        MonitorService.sync(getApplication())
    }

    private fun persistFavoritesIfDirty() {
        if (favoritesDirty) persistFavorites()
    }

    fun calibrate() {
        val s = _track.value?.smoothed ?: return
        val v = Math.round(s).toInt()
        _rssiAt1m.value = v
        prefs.edit().putInt("rssiAt1m", v).apply()
    }

    fun resetCalibration() {
        _rssiAt1m.value = Proximity.DEFAULT_RSSI_AT_1M
        prefs.edit().remove("rssiAt1m").apply()
    }

    fun setVibrate(on: Boolean) {
        _vibrate.value = on
        prefs.edit().putBoolean("vibrate", on).apply()
    }

    fun setAlert(id: String, on: Boolean) {
        _favorites.update { list -> list.map { if (it.id == id) it.copy(alertWhenLeft = on) else it } }
        persistFavorites()
    }

    fun setBeep(on: Boolean) {
        _beep.value = on
        prefs.edit().putBoolean("beep", on).apply()
    }

    private suspend fun tickLoop() {
        var ticks = 0
        while (coroutineContext.isActive) {
            delay(500)
            ticks++
            val now = SystemClock.elapsedRealtime()
            live.values.removeAll { now - it.lastSeen >= DEVICE_EXPIRE_MS }
            _devices.value = HashMap(live)

            // A favorite we're searching for: wait ~3 s after scanning starts so every nearby
            // look-alike can show up, then take its exact address or the strongest look-alike.
            val t0 = _track.value
            if (t0 != null && t0.searching && now - scanStartedAt > AUTO_OPEN_WAIT_MS) {
                val fav = _favorites.value.firstOrNull { it.id == t0.favoriteId }
                fav?.bestMatch(_devices.value.values)?.let { openOn(it, fav.id) }
            }

            _track.update { t ->
                if (t == null || t.searching) return@update t
                val fresh = now - t.lastSeen < LOST_AFTER_MS
                val sample = if (fresh) (t.smoothed ?: -100.0).toFloat() else -100f
                val hist = (t.history + sample).takeLast(HISTORY_SAMPLES)
                val lookalikes = if (!t.kind.isTracker) 0 else _devices.value.values.count {
                    it.address != t.address && it.fingerprint == t.fingerprint
                }
                t.copy(
                    history = hist, trend = if (fresh) Proximity.trend(hist) else Trend.STEADY,
                    lookalikes = lookalikes,
                )
            }
            if (ticks % 20 == 0) persistFavoritesIfDirty()
            // Android quietly weakens unfiltered scans after ~30 minutes; restart well before.
            if (scanning && now - scanStartedAt > 20 * 60_000L) applyScan()
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanOnly() {
        if (scanning) runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        scanning = false
    }

    private suspend fun beepLoop() {
        while (coroutineContext.isActive) {
            val t = _track.value
            val now = SystemClock.elapsedRealtime()
            val s = t?.smoothed
            val wantBeep = _beep.value
            val wantBuzz = _vibrate.value
            if ((wantBeep || wantBuzz) && t != null && !t.searching && s != null && now - t.lastSeen < LOST_AFTER_MS) {
                if (wantBeep) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
                if (wantBuzz) runCatching {
                    vibrator?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
                }
                val heat = Proximity.heat(s)
                delay((1500 - heat * 1380).toLong()) // 1.5 s when cold, ~0.12 s when hot
            } else {
                delay(300)
            }
        }
    }

    override fun onCleared() {
        stop()
        tone?.release()
    }

    companion object {
        const val PREFS = "settings"
        const val LOST_AFTER_MS = 8_000L
        const val DEVICE_EXPIRE_MS = 30_000L
        const val HISTORY_SAMPLES = 120 // 60 s at 2 samples/s
        const val AUTO_OPEN_WAIT_MS = 3_000L
    }
}
