package com.example.trackerfinder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Background "left behind" monitor. Runs as a foreground service (Android requires a visible
 * notification for continuous background Bluetooth scanning) while at least one favorite has
 * its alert turned on. Uses filtered, balanced-power scans so it keeps working with the screen off.
 */
class MonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var scanning = false
    private var startedAt = 0L
    private val devices = HashMap<String, SeenDevice>()
    /** Favorites heard at least once since monitoring started (we only alert after that). */
    private val armed = HashSet<String>()
    private val alerted = HashSet<String>()
    private var scannedKinds: Set<TrackerKind> = emptySet()
    private var scannedToken: String? = null
    /** Cached list of favorites with alerts on; refreshed every tick. */
    private var watched: List<Favorite> = emptyList()

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = onResult(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::onResult)
    }

    private val tick = object : Runnable {
        override fun run() {
            evaluate()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private var shownCount = -1
    private var btOn = true

    /** Bluetooth off means silence, not "left behind": pause alerts and re-arm when it's back. */
    private val btReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, -1)) {
                android.bluetooth.BluetoothAdapter.STATE_OFF -> { btOn = false; stopScan(); armed.clear() }
                android.bluetooth.BluetoothAdapter.STATE_ON -> { btOn = true; restartScanIfNeeded(watched) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        btOn = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.isEnabled == true
        ContextCompat.registerReceiver(
            this, btReceiver, android.content.IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        createChannels(this)
        startedAt = System.currentTimeMillis()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        watched = watchedFavorites()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        try {
            ServiceCompat.startForeground(this, ONGOING_ID, ongoingNotification(watched.size), type)
            shownCount = watched.size * 2 + if (FindPhone.enabled(this)) 1 else 0
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (watched.isEmpty() && !FindPhone.enabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        restartScanIfNeeded(watched)
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, TICK_MS)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        runCatching { unregisterReceiver(btReceiver) }
        handler.removeCallbacks(tick)
        stopScan()
        super.onDestroy()
    }

    private fun watchedFavorites(): List<Favorite> =
        FavoritesStore.load(getSharedPreferences(ScanViewModel.PREFS, Context.MODE_PRIVATE)).filter { it.alertWhenLeft }

    @SuppressLint("MissingPermission")
    private fun restartScanIfNeeded(watched: List<Favorite>) {
        val kinds = watched.map { it.kind }.toSet()
        val token = if (FindPhone.enabled(this)) FindPhone.token(this) else null
        if (scanning && kinds == scannedKinds && token == scannedToken) return
        stopScan()
        val scanner = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bluetoothLeScanner ?: return
        if (!hasScanPermission()) return
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
        try {
            val filters = filtersFor(watched) + listOfNotNull(if (token != null) FindPhone.scanFilter(this) else null)
            if (filters.isEmpty()) return
            scanner.startScan(filters, settings, callback)
            scanning = true
            scannedKinds = kinds
            scannedToken = token
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!scanning) return
        runCatching {
            (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bluetoothLeScanner?.stopScan(callback)
        }
        scanning = false
    }

    private fun hasScanPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.ACCESS_FINE_LOCATION
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    /** Android only keeps scanning with the screen off when the scan has filters. */
    private fun filtersFor(watched: List<Favorite>): List<ScanFilter> {
        val filters = mutableListOf<ScanFilter>()
        val kinds = watched.map { it.kind }.toSet()
        if (kinds.any { it == TrackerKind.GOOGLE_FIND_HUB || it == TrackerKind.GOOGLE_FIND_HUB_SEPARATED }) {
            filters += ScanFilter.Builder().setServiceData(ParcelUuid.fromString(uuid16(AdvertParser.EDDYSTONE_UUID16)), ByteArray(0)).build()
        }
        if (TrackerKind.FAST_PAIR in kinds) {
            filters += ScanFilter.Builder().setServiceData(ParcelUuid.fromString(uuid16(AdvertParser.FAST_PAIR_UUID16)), ByteArray(0)).build()
        }
        if (TrackerKind.APPLE_FIND_MY in kinds) {
            filters += ScanFilter.Builder().setManufacturerData(AdvertParser.APPLE_COMPANY_ID, byteArrayOf(0x12)).build()
        }
        watched.filter { it.kind == TrackerKind.NAMED_HOCO || it.kind == TrackerKind.OTHER }.forEach { f ->
            filters += if (f.name != null) ScanFilter.Builder().setDeviceName(f.name).build()
            else ScanFilter.Builder().setDeviceAddress(f.lastAddress).build()
        }
        return filters
    }

    private fun onResult(r: ScanResult) {
        val now = SystemClock.elapsedRealtime()
        if (FindPhone.matches(this, r)) PhoneAlarm.ring(this)
        val seen = parseScan(r, devices[r.device.address], now)
        devices[seen.address] = seen
        if (devices.size > 200) devices.values.removeAll { now - it.lastSeen > 60_000 }
        var changed = false
        watched.forEach { f ->
            if (f.matches(seen)) {
                changed = Presence.heard(this, f.id) || changed
                armed += f.id
                if (alerted.remove(f.id)) {
                    getSystemService(NotificationManager::class.java).cancel(alertId(f.id))
                }
            }
        }
        if (changed) FavoritesWidget.updateAll(this)
    }

    private fun evaluate() {
        watched = watchedFavorites()
        if (watched.isEmpty() && !FindPhone.enabled(this)) {
            stopSelf()
            return
        }
        val nm = getSystemService(NotificationManager::class.java)
        val shownKey = watched.size * 2 + if (FindPhone.enabled(this)) 1 else 0
        if (shownKey != shownCount) {
            shownCount = shownKey
            nm.notify(ONGOING_ID, ongoingNotification(watched.size))
        }
        if (!btOn) return
        restartScanIfNeeded(watched)
        val now = System.currentTimeMillis()
        watched.forEach { f ->
            val last = Presence.lastHeard(this, f.id)
            val gone = now - last > LEFT_BEHIND_MS
            if (f.id in armed && gone && f.id !in alerted && !AlertRules.suppressLeftBehind(this)) {
                alerted += f.id
                nm.notify(alertId(f.id), leftBehindNotification(f, last))
            }
        }
        FavoritesWidget.updateAll(this)
    }

    private fun openAppIntent(favoriteId: String?, requestCode: Int): PendingIntent {
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (favoriteId != null) i.putExtra(MainActivity.EXTRA_FAVORITE_ID, favoriteId)
        return PendingIntent.getActivity(this, requestCode, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun ongoingNotification(count: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_tracker)
            .setContentTitle(if (count > 0) "Watching $count tracker${if (count == 1) "" else "s"}" else "Find my phone is on")
            .setContentText(
                when {
                    count > 0 && FindPhone.enabled(this) -> "Left-behind alerts and Find my phone are on"
                    count > 0 -> "You'll be alerted if one is left behind"
                    else -> "Press your tracker's button to ring this phone"
                },
            )
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent(null, 0))
            .build()

    private fun leftBehindNotification(f: Favorite, lastHeard: Long): Notification {
        val mins = ((System.currentTimeMillis() - lastHeard) / 60_000).coerceAtLeast(1)
        return NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_tracker)
            .setContentTitle("Did you leave ${f.nickname} behind?")
            .setContentText("Not heard for $mins min. Tap to start searching.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(f.id, alertId(f.id)))
            .build()
    }

    companion object {
        private const val CHANNEL_ONGOING = "monitor"
        private const val CHANNEL_ALERTS = "left_behind"
        private const val ONGOING_ID = 1
        private const val TICK_MS = 15_000L
        /** How long a favorite must be silent before we call it "left behind". */
        const val LEFT_BEHIND_MS = 2 * 60_000L

        @Volatile var running = false
            private set

        // Own range so it never collides with Find Hub alerts (5000-30000).
        private fun alertId(favoriteId: String) = 100_000 + (favoriteId.hashCode() and 0xFFFF)

        private fun uuid16(v: Int) = String.format("0000%04x-0000-1000-8000-00805f9b34fb", v)

        fun createChannels(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ONGOING, "Background monitoring", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Shown while the app watches for left-behind trackers" }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERTS, "Left-behind alerts", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "When a favorite tracker drops out of range" }
            )
        }

        /**
         * Starts the monitor if any favorite has alerts on and it isn't running, or stops it if none do.
         * Starting only works while the app is in the foreground, so failures are ignored here.
         */
        fun sync(ctx: Context) {
            val any = FavoritesStore.load(ctx.getSharedPreferences(ScanViewModel.PREFS, Context.MODE_PRIVATE)).any { it.alertWhenLeft } ||
                FindPhone.enabled(ctx)
            val intent = Intent(ctx, MonitorService::class.java)
            if (!any) {
                if (running) ctx.stopService(intent)
                return
            }
            if (running) return
            runCatching { ContextCompat.startForegroundService(ctx, intent) }
        }
    }
}
