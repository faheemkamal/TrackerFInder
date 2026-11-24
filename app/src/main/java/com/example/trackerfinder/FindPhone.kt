package com.example.trackerfinder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

sealed interface LearnState {
    data object Idle : LearnState
    data object Baseline : LearnState          // listening to what's normal for a few seconds
    data class Press(val secondsLeft: Int) : LearnState
    data class Found(val token: String) : LearnState
    data object NotFound : LearnState
}

/**
 * "Find my phone": press the tracker's button and the phone rings.
 *
 * Trackers don't share a standard for this, so the app learns it: it listens to what's normal
 * near the phone, then asks you to press the button and looks for a kind of Bluetooth signal that
 * only appears then (for example a pairing or "identify me" advert). That signal is saved and the
 * background monitor rings the phone when it hears it close by.
 */
object FindPhone {
    private const val PREFS = "find_phone"
    private const val BASELINE_MS = 4_000L
    private const val PRESS_MS = 12_000L
    /** Only react to strong signals: the tracker has to be close to the phone (≈ a few metres). */
    const val MIN_RSSI = -80
    private const val LEARN_MIN_RSSI = -72

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = p(ctx).getBoolean("on", false) && token(ctx) != null
    fun token(ctx: Context): String? = p(ctx).getString("token", null)
    fun setEnabled(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean("on", on).apply(); MonitorService.sync(ctx) }
    fun forget(ctx: Context) { p(ctx).edit().clear().apply(); MonitorService.sync(ctx) }

    private val _learn = MutableStateFlow<LearnState>(LearnState.Idle)
    val learn: StateFlow<LearnState> = _learn.asStateFlow()
    private var learnStart = 0L
    private val baseline = HashSet<String>()
    private val counts = HashMap<String, Int>()
    private val handler = Handler(Looper.getMainLooper())

    fun startLearning(ctx: Context) {
        baseline.clear(); counts.clear()
        learnStart = SystemClock.elapsedRealtime()
        _learn.value = LearnState.Baseline
        val app = ctx.applicationContext
        fun tick() {
            val t = SystemClock.elapsedRealtime() - learnStart
            when {
                _learn.value !is LearnState.Baseline && _learn.value !is LearnState.Press -> return
                t < BASELINE_MS -> handler.postDelayed(::tick, 250)
                t < BASELINE_MS + PRESS_MS -> {
                    _learn.value = LearnState.Press(((BASELINE_MS + PRESS_MS - t) / 1000).toInt() + 1)
                    // Stop early once the new signal has clearly shown up a few times.
                    if ((counts.values.maxOrNull() ?: 0) >= 3) finish(app) else handler.postDelayed(::tick, 250)
                }
                else -> finish(app)
            }
        }
        tick()
    }

    fun cancelLearning() { _learn.value = LearnState.Idle }

    private fun finish(ctx: Context) {
        val best = counts.maxByOrNull { it.value }?.key
        _learn.value = if (best != null) LearnState.Found(best) else LearnState.NotFound
        if (best != null) {
            p(ctx).edit().putString("token", best).putBoolean("on", true).apply()
            MonitorService.sync(ctx)
        }
    }

    /** Called for every advert the app hears (foreground scanning). */
    fun onAdvert(ctx: Context, r: ScanResult) {
        when (_learn.value) {
            LearnState.Baseline, is LearnState.Press -> {
                if (r.rssi < LEARN_MIN_RSSI) return
                val tokens = advertTokens(r)
                if (_learn.value == LearnState.Baseline) baseline += tokens
                else tokens.filter { it !in baseline }.forEach { counts[it] = (counts[it] ?: 0) + 1 }
            }
            else -> if (matches(ctx, r)) PhoneAlarm.ring(ctx)
        }
    }

    fun matches(ctx: Context, r: ScanResult): Boolean {
        if (r.rssi < MIN_RSSI || !p(ctx).getBoolean("on", false)) return false
        val t = token(ctx) ?: return false
        return t in advertTokens(r)
    }

    /** Hardware scan filter for the learned signal, so detection keeps working with the screen off. */
    fun scanFilter(ctx: Context): ScanFilter? {
        val t = token(ctx) ?: return null
        val parts = t.split(":")
        return runCatching {
            when (parts[0]) {
                "svc" -> if (parts.size == 3) ScanFilter.Builder()
                    .setServiceData(uuid16(parts[1].toInt(16)), byteArrayOf(parts[2].toInt(16).toByte()), byteArrayOf(0xFF.toByte())).build()
                else ScanFilter.Builder().setServiceData(uuid16(parts[1].toInt(16)), ByteArray(0)).build()
                "uuid" -> ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(t.removePrefix("uuid:")))).build()
                "mfr" -> if (parts.size == 3) ScanFilter.Builder()
                    .setManufacturerData(parts[1].toInt(16), byteArrayOf(parts[2].toInt(16).toByte()), byteArrayOf(0xFF.toByte())).build()
                else ScanFilter.Builder().setManufacturerData(parts[1].toInt(16), ByteArray(0)).build()
                "name" -> ScanFilter.Builder().setDeviceName(t.removePrefix("name:")).build()
                else -> null
            }
        }.getOrNull()
    }

    /** Plain-words description of the learned signal for the settings screen. */
    fun describe(token: String): String = when {
        token.startsWith("svc:feaa:41") -> "a Find Hub \"separated\" signal"
        token.startsWith("svc:fe2c") -> "a Fast Pair (pairing) signal"
        token.startsWith("svc:feaa") -> "a Find Hub signal"
        token.startsWith("name:") -> "a signal named \"${token.removePrefix("name:")}\""
        token.startsWith("mfr:") -> "a maker-specific signal"
        else -> "a Bluetooth service signal"
    }

    private fun uuid16(v: Int) = ParcelUuid.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", v))
}

/** Loud alarm on the phone, with a Stop button. Stops by itself after 45 s. */
object PhoneAlarm {
    private const val CHANNEL = "find_phone"
    private const val NOTIF_ID = 7700
    private var player: MediaPlayer? = null
    private var savedVolume = -1
    private var lastStop = 0L
    private val handler = Handler(Looper.getMainLooper())

    val ringing: Boolean get() = player != null

    fun ring(ctx: Context) {
        handler.post { start(ctx.applicationContext) }
    }

    private fun start(ctx: Context) {
        if (player != null || SystemClock.elapsedRealtime() - lastStop < 20_000) return
        val am = ctx.getSystemService(AudioManager::class.java)
        runCatching {
            savedVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        }
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(ctx, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        vibrator(ctx)?.let { v -> runCatching { v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0)) } }
        notify(ctx)
        handler.postDelayed({ stop(ctx) }, 45_000)
    }

    fun stop(ctx: Context) {
        handler.removeCallbacksAndMessages(null)
        player?.let { runCatching { it.stop() }; it.release() }
        if (player != null) lastStop = SystemClock.elapsedRealtime()
        player = null
        vibrator(ctx)?.cancel()
        if (savedVolume >= 0) runCatching {
            ctx.getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0)
        }
        savedVolume = -1
        NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)
    }

    private fun vibrator(ctx: Context): Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private fun notify(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Find my phone", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rings when you press your tracker's button"
            setSound(null, null)
        })
        val stop = PendingIntent.getBroadcast(ctx, NOTIF_ID, Intent(ctx, StopAlarmReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_tracker)
            .setContentTitle("Here I am!")
            .setContentText("Your tracker's button was pressed. Tap Stop to silence.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(stop)
            .addAction(R.drawable.ic_stat_tracker, "Stop", stop)
            .setDeleteIntent(stop)
            .build()
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return // still rings; just no Stop button in the shade
        try { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n) } catch (_: SecurityException) {}
    }
}

class StopAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = PhoneAlarm.stop(context.applicationContext)
}
