package com.example.trackerfinder.findhub

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Background Find Hub check: fetch every tag's location, store it, run alerts and backups,
 * then schedule the next check. Android's periodic jobs can't run more often than every 15 min,
 * so the worker re-schedules itself as a one-off job each time (Android may still delay it
 * while the phone is idle in Doze).
 */
class PollWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        try {
            val acct = FindHub.account(ctx)
            if (!acct.connected) {
                HubSettings.recordCheck(ctx, "Google account not connected")
                return Result.success()
            }
            val added = FindHub.pollAll(ctx)
            // The engine reports its own failures in the check log rather than throwing.
            val failed = runCatching { FindHub.checkLog(ctx).firstOrNull()?.ok == false }.getOrDefault(false)
            HubSettings.recordCheck(ctx, if (failed) "Last check failed (no network or Google refused it)" else null)
            if (HubSettings.statusNotify(ctx)) HubNotify.status(ctx, added)
            if (added > 0) afterNewLocations(ctx)
            runCatching { HubSettings.saveWidgetTags(ctx, FindHub.devices(ctx)) }
            com.example.trackerfinder.TagWidget.refresh(ctx)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            HubSettings.recordCheck(ctx, FindHub.friendly(e))
        } finally {
            // Only the scheduled chain re-schedules itself. A "check now" run must not, or it
            // would add a second chain and checks would run twice as often.
            if (CHAIN in tags && HubSettings.backgroundOn(ctx)) scheduleNext(ctx)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "findhub-check"
        private const val CHAIN = "findhub-chain"

        /** Movement and area alerts plus file backup, after any check that saved new locations. */
        suspend fun afterNewLocations(ctx: Context) {
            if (HubSettings.moveAlerts(ctx)) runCatching {
                val moves = FindHub.newMovements(ctx)
                for (i in 0 until moves.length()) {
                    val m = moves.getJSONObject(i)
                    HubNotify.moved(ctx, m.getString("id"), m.getString("name"), m.getInt("moved_m"), m.getDouble("lat"), m.getDouble("lon"))
                }
            }
            runCatching { checkGeofences(ctx) }
            backupToFileIfSet(ctx)
        }

        private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Starts background checks if they're on and not already queued. */
        fun ensureScheduled(ctx: Context) {
            if (!HubSettings.backgroundOn(ctx)) return
            val req = OneTimeWorkRequestBuilder<PollWorker>()
                .addTag(CHAIN)
                .setConstraints(constraints())
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, req)
        }

        /** After a settings change: replace whatever is queued with a fresh chain. */
        fun restart(ctx: Context) {
            if (!HubSettings.backgroundOn(ctx)) { stop(ctx); return }
            val req = OneTimeWorkRequestBuilder<PollWorker>()
                .addTag(CHAIN)
                .setConstraints(constraints())
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, req)
        }

        fun scheduleNext(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<PollWorker>()
                .addTag(CHAIN)
                .setConstraints(constraints())
                .setInitialDelay(HubSettings.interval(ctx).toLong(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }

        /** When the next background check is due (wall clock), if one is queued. */
        suspend fun nextRunAt(ctx: Context): Long? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            WorkManager.getInstance(ctx).getWorkInfosForUniqueWork(NAME).get()
                .filter { it.state == androidx.work.WorkInfo.State.ENQUEUED }
                .map { it.nextScheduleTimeMillis }
                .filter { it in 1 until Long.MAX_VALUE }
                .minOrNull()
        }

        fun stop(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(NAME)
        }

        /** Area alerts: notify on leaving (and on coming back into) each tag's circle. */
        suspend fun checkGeofences(ctx: Context) {
            FindHub.devices(ctx).forEach { d ->
                val fence = HubSettings.geofence(ctx, d.id) ?: return@forEach
                val lat = d.lastLat ?: return@forEach
                val lon = d.lastLon ?: return@forEach
                val inside = Geo.distanceM(fence.lat, fence.lon, lat, lon) <= fence.radiusM
                if (fence.wasInside == true && !inside) HubNotify.leftArea(ctx, d.id, d.name, fence.radiusM, lat, lon)
                if (fence.wasInside == false && inside) HubNotify.backInArea(ctx, d.id, d.name)
                if (fence.wasInside != inside) HubSettings.setGeofence(ctx, d.id, fence.copy(wasInside = inside))
            }
        }

        /** Writes the full history as CSV to the file the user picked (e.g. in Google Drive). */
        suspend fun backupToFileIfSet(ctx: Context): String? {
            val uri = HubSettings.backupFileUri(ctx) ?: return null
            val tmp = File(ctx.cacheDir, "backup.csv")
            return try {
                FindHub.exportCsv(ctx, tmp, null, 0, 1L shl 40)
                val out = runCatching { ctx.contentResolver.openOutputStream(Uri.parse(uri), "wt") }.getOrNull()
                    ?: ctx.contentResolver.openOutputStream(Uri.parse(uri), "w")
                    ?: error("Can't write to the backup file")
                out.use { o -> tmp.inputStream().use { it.copyTo(o) } }
                HubSettings.setLastFileBackup(ctx, System.currentTimeMillis())
                null
            } catch (e: Exception) {
                FindHub.friendly(e)
            } finally {
                tmp.delete()
            }
        }
    }
}
