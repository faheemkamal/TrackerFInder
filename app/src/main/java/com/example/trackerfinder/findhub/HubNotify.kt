package com.example.trackerfinder.findhub

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.trackerfinder.MainActivity
import com.example.trackerfinder.R

object HubNotify {
    private const val CHANNEL = "findhub_alerts"

    private fun ensureChannel(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Find Hub alerts", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "When a tag moves or leaves an area you set" },
        )
    }

    private fun post(ctx: Context, deviceId: String, idOffset: Int, title: String, text: String, lat: Double? = null, lon: Double? = null) {
        if (com.example.trackerfinder.AlertRules.inQuietHours(ctx)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val open = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_HUB_DEVICE_ID, deviceId)
        val id = 5000 + idOffset + (deviceId.hashCode() and 0xFFF) * 4
        val pi = PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_tracker)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .apply {
                // Quick actions, as in Tag Tracker Open: ring the tag, or open the spot in a maps app.
                val ring = PendingIntent.getBroadcast(ctx, id + 1, Intent(ctx, RingReceiver::class.java)
                    .putExtra(RingReceiver.EXTRA_ID, deviceId),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                addAction(R.drawable.ic_stat_tracker, "Ring tag", ring)
                if (lat != null && lon != null) {
                    val maps = PendingIntent.getActivity(ctx, id + 2,
                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:$lat,$lon?q=$lat,$lon")),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    addAction(R.drawable.ic_stat_tracker, "Open in Maps", maps)
                }
            }
            .build()
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) {}
    }

    fun moved(ctx: Context, id: String, name: String, meters: Int, lat: Double, lon: Double) =
        post(ctx, id, 0, "$name is moving", "It moved about $meters m since its last report. Tap to see it on the map.", lat, lon)

    fun leftArea(ctx: Context, id: String, name: String, radius: Int, lat: Double, lon: Double, place: String? = null) =
        post(ctx, id, 1, if (place != null) "$name left ${short(place)}" else "$name left its area",
            "Its latest location is more than $radius m from ${place?.let(::short) ?: "the center of the area you set"}. Tap to see where it is.", lat, lon)

    private fun short(place: String) = place.split(",").first().trim().take(40)

    /** Quiet status after a background check (optional, like Tag Tracker Open's sync status). */
    fun status(ctx: Context, added: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("findhub_status", "Find Hub check status", NotificationManager.IMPORTANCE_MIN))
        val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())
        val open = PendingIntent.getActivity(ctx, 4999, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, "findhub_status")
            .setSmallIcon(R.drawable.ic_stat_tracker)
            .setContentTitle(if (added > 0) "$added new location${if (added == 1) "" else "s"} saved" else "Checked Find Hub")
            .setContentText("Last check $time · next in about ${HubSettings.interval(ctx)} min")
            .setSilent(true)
            .setContentIntent(open)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(4999, n) } catch (_: SecurityException) {}
    }

    fun backInArea(ctx: Context, id: String, name: String, place: String? = null) =
        post(ctx, id, 2, if (place != null) "$name is back at ${short(place)}" else "$name is back in its area",
            "Its latest location is inside the area you set again.")

    fun battery(ctx: Context, id: String, level: String) {
        val name = runCatching {
            val arr = HubSettings.widgetTags(ctx)
            (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull { it.getString("id") == id }?.getString("name")
        }.getOrNull() ?: "Your tag"
        post(ctx, id, 3, if (level == "critical") "$name battery is very low" else "$name battery is low",
            "Replace or charge it soon so it keeps showing up in Find Hub.")
    }
}
