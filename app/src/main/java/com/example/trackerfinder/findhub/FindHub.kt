package com.example.trackerfinder.findhub

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

data class HubAccount(val signedIn: Boolean, val unlocked: Boolean, val email: String, val error: String?) {
    val connected get() = signedIn && unlocked
}

data class HubDevice(
    val id: String,
    val name: String,
    val lastTs: Long?,
    val lastLat: Double?,
    val lastLon: Double?,
    val lastAccuracy: Double?,
    val pointCount: Int,
    val lastPolledAt: Long?,
    val lastError: String?,
)

data class CheckRecord(val ts: Long, val ms: Long, val added: Int, val ok: Boolean)

data class HubPoint(
    val ts: Long,
    val lat: Double?,
    val lon: Double?,
    val accuracy: Double?,
    val isOwnReport: Boolean,
    val semanticName: String?,
)

/**
 * Kotlin side of the Find Hub engine. The engine itself is Python (GoogleFindMyTools and
 * Tag Tracker Open's server code, both GPL-3.0) running inside the app through Chaquopy.
 * Every call runs on one background thread: Python isn't fast to start and must never block the UI.
 */
object FindHub {
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "findhub") }.asCoroutineDispatcher()

    @Volatile private var module: PyObject? = null

    /** Blocking. Call only from the engine thread (or a worker thread). */
    @Synchronized
    fun py(ctx: Context): PyObject {
        module?.let { return it }
        if (!Python.isStarted()) Python.start(AndroidPlatform(ctx.applicationContext))
        val data = File(ctx.filesDir, "findhub").apply { mkdirs() }
        val m = Python.getInstance().getModule("tagapp")
        m.callAttr("init", data.absolutePath)
        module = m
        return m
    }

    suspend fun <T> call(ctx: Context, block: (PyObject) -> T): T = withContext(dispatcher) { block(py(ctx)) }

    /** Python exceptions arrive wrapped; show just the message. */
    fun friendly(e: Throwable): String {
        val msg = e.message ?: e.toString()
        return msg.substringAfterLast("Error: ").ifBlank { msg }.lines().first().take(300)
    }

    suspend fun account(ctx: Context): HubAccount = call(ctx) { m ->
        val o = JSONObject(m.callAttr("account_json").toString())
        HubAccount(o.optBoolean("signed_in"), o.optBoolean("unlocked"), o.optString("email"),
            o.optString("error").ifBlank { null })
    }

    suspend fun devices(ctx: Context): List<HubDevice> = call(ctx) { m ->
        val arr = JSONObject(m.callAttr("devices_json", 10, false).toString()).getJSONArray("devices")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            HubDevice(
                id = o.getString("id"), name = o.optString("name", "Tag"),
                lastTs = o.optLongOrNull("last_ts"), lastLat = o.optDoubleOrNull("last_lat"),
                lastLon = o.optDoubleOrNull("last_lon"), lastAccuracy = o.optDoubleOrNull("last_accuracy"),
                pointCount = o.optInt("point_count"), lastPolledAt = o.optLongOrNull("last_polled_at"),
                lastError = o.optString("last_error").takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }

    suspend fun history(ctx: Context, id: String, start: Long, end: Long): List<HubPoint> = call(ctx) { m ->
        val arr = JSONObject(m.callAttr("history_json", id, start, end).toString()).getJSONArray("points")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            HubPoint(
                ts = o.getLong("ts"), lat = o.optDoubleOrNull("lat"), lon = o.optDoubleOrNull("lon"),
                accuracy = o.optDoubleOrNull("accuracy"), isOwnReport = o.optInt("is_own_report", 0) == 1,
                semanticName = o.optString("semantic_name").takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }

    suspend fun refreshTagList(ctx: Context) = call(ctx) { it.callAttr("list_tags_json"); Unit }
    suspend fun locateNow(ctx: Context, id: String): Int = call(ctx) { it.callAttr("locate_now", id).toInt() }
    suspend fun pollAll(ctx: Context): Int = call(ctx) { it.callAttr("poll").toInt() }
    suspend fun ring(ctx: Context, id: String, start: Boolean) = call(ctx) {
        it.callAttr(if (start) "play_sound" else "stop_sound", id); Unit
    }
    suspend fun rename(ctx: Context, id: String, name: String) = call(ctx) { it.callAttr("rename", id, name); Unit }
    suspend fun signIn(ctx: Context, oauthToken: String): String = call(ctx) { it.callAttr("sign_in", oauthToken).toString() }
    suspend fun sharedKeyUrl(ctx: Context): String = call(ctx) { it.callAttr("shared_key_url").toString() }
    suspend fun saveVaultKeys(ctx: Context, keys: String) = call(ctx) { it.callAttr("save_vault_keys", keys); Unit }
    suspend fun importSecrets(ctx: Context, path: String) = call(ctx) { it.callAttr("import_secrets", path); Unit }

    suspend fun importCsv(ctx: Context, path: String): Int = call(ctx) { it.callAttr("import_csv", path).toInt() }

    suspend fun exportCsv(ctx: Context, file: File, id: String?, start: Long, end: Long): Int =
        call(ctx) { it.callAttr("export_csv", file.absolutePath, id, start, end).toInt() }
    suspend fun exportGpx(ctx: Context, file: File, id: String, start: Long, end: Long): Int =
        call(ctx) { it.callAttr("export_gpx", file.absolutePath, id, start, end).toInt() }

    // ----- Google Drive backup -----
    suspend fun setDriveAuto(ctx: Context, on: Boolean) = call(ctx) { it.callAttr("set_drive_default", on); Unit }
    suspend fun driveStatus(ctx: Context): JSONObject = call(ctx) { JSONObject(it.callAttr("drive_status_json").toString()) }
    suspend fun driveUploadNow(ctx: Context): String = call(ctx) { it.callAttr("drive_default_upload").toString() }
    suspend fun driveVerify(ctx: Context): JSONObject = call(ctx) { JSONObject(it.callAttr("drive_verify_json").toString()) }
    suspend fun driveRestore(ctx: Context): Int = call(ctx) { it.callAttr("drive_default_restore").toInt() }

    /** Movement alerts: tags whose newest point is >150 m from the previous one (each reported once). */
    suspend fun newMovements(ctx: Context): JSONArray = call(ctx) { JSONArray(it.callAttr("new_movements").toString()) }

    /** Recent background/manual checks recorded by the engine, newest first. */
    suspend fun checkLog(ctx: Context): List<CheckRecord> = call(ctx) { m ->
        val arr = JSONObject(m.callAttr("battery_log").toString()).getJSONArray("checks")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CheckRecord(o.getLong("ts"), o.optLong("ms"), o.optInt("added"), o.optBoolean("ok", true))
        }
    }

    /** True once the engine is loaded (so background callers don't start Python just for this). */
    val isStarted: Boolean get() = module != null

    /** Battery level from a nearby tag's advert (service data 0xFEAA); null if it isn't one of ours. */
    suspend fun beaconMatch(ctx: Context, serviceData: ByteArray): JSONObject? = call(ctx) { m ->
        m.callAttr("beacon_match", serviceData.joinToString("") { "%02x".format(it) }).toString()
            .takeIf { it.isNotBlank() }?.let(::JSONObject)
    }

    /** The Google login file (tokens). Same format as Tag Tracker's desktop "google-login" file. */
    fun secretsFile(ctx: Context) = File(File(ctx.filesDir, "findhub"), "google_secrets.json")

    /** Signs out: deletes the Google tokens (history stays). */
    suspend fun signOut(ctx: Context) = withContext(dispatcher) {
        File(File(ctx.filesDir, "findhub"), "google_secrets.json").delete()
        File(File(ctx.filesDir, "findhub"), "beacon_keys.json").delete()
        Unit
    }
}

private fun JSONObject.optLongOrNull(k: String): Long? = if (isNull(k) || !has(k)) null else optLong(k)
private fun JSONObject.optDoubleOrNull(k: String): Double? = if (isNull(k) || !has(k)) null else optDouble(k).takeIf { !it.isNaN() }
