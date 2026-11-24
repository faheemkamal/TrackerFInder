package com.example.trackerfinder.findhub

import android.app.Application
import android.net.Uri
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
import java.io.File

enum class HubRange(val label: String, val seconds: Long?) {
    TODAY("Today", null),
    H24("24 h", 86_400L),
    D2("2 days", 2 * 86_400L),
    D7("7 days", 7 * 86_400L),
    D30("30 days", 30 * 86_400L),
    D90("90 days", 90 * 86_400L),
    Y1("1 year", 365 * 86_400L),
    CUSTOM("Custom", null),
}

data class HubUi(
    val loading: Boolean = true,
    val account: HubAccount? = null,
    val devices: List<HubDevice> = emptyList(),
    val selectedId: String? = null,
    val range: HubRange = HubRange.TODAY,
    val customStart: Long? = null, // unix seconds
    val customEnd: Long? = null,
    val history: List<HubPoint> = emptyList(),
    val stats: TripStats? = null,
    val busy: String? = null,      // e.g. "Locating…"
    val message: String? = null,   // one-off snackbar text
    val ringing: Boolean = false,
    val replayIndex: Int? = null,  // null = show whole path
    val replaying: Boolean = false,
    val checks: List<CheckRecord> = emptyList(),
    val totalSaved: Int? = null,
    val nextCheckAt: Long? = null,  // wall-clock millis, estimate from WorkManager
    val intervalMin: Int = 10,
    val backgroundOn: Boolean = true,
    val guard: Boolean = false,
    val showPoints: Boolean = false,
    val showHeat: Boolean = false,
    val avgCheckMs: Long? = null,
) {
    val selected: HubDevice? get() = devices.firstOrNull { it.id == selectedId }
    val geoHistory: List<HubPoint> get() = history.filter { it.lat != null && it.lon != null }
}

class HubViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val _ui = MutableStateFlow(HubUi())
    val ui: StateFlow<HubUi> = _ui.asStateFlow()
    private var replayJob: Job? = null

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        try {
            val acct = FindHub.account(ctx)
            val devices = if (acct.signedIn) FindHub.devices(ctx) else emptyList()
            _ui.update { u ->
                u.copy(
                    loading = false, account = acct, devices = devices,
                    selectedId = u.selectedId?.takeIf { id -> devices.any { it.id == id } } ?: devices.firstOrNull()?.id,
                )
            }
            if (acct.connected) PollWorker.ensureScheduled(ctx)
            HubSettings.saveWidgetTags(ctx, devices)
            com.example.trackerfinder.TagWidget.refresh(ctx)
            loadHistory()
            loadCheckInfo()
        } catch (e: Exception) {
            _ui.update { it.copy(loading = false, message = "Find Hub engine error: ${FindHub.friendly(e)}") }
        }
    }

    /** After sign-in: fetch the tag list from Google, then the first locations. */
    fun afterSetupStep() = viewModelScope.launch {
        val acct = runCatching { FindHub.account(ctx) }.getOrNull()
        if (acct?.signedIn == true) {
            _ui.update { it.copy(busy = "Loading your tags…") }
            runCatching { FindHub.refreshTagList(ctx) }.onFailure { e -> toast("Couldn't load tags: ${FindHub.friendly(e)}") }
            _ui.update { it.copy(busy = null) }
        }
        refresh()
        if (acct?.connected == true) checkAllNow()
    }

    fun loadCheckInfo() = viewModelScope.launch {
        val checks = runCatching { FindHub.checkLog(ctx) }.getOrDefault(emptyList())
        val total = runCatching { FindHub.driveStatus(ctx).optInt("locations") }.getOrNull()
        val next = runCatching { PollWorker.nextRunAt(ctx) }.getOrNull()
        _ui.update {
            it.copy(
                checks = checks, totalSaved = total, nextCheckAt = next,
                intervalMin = HubSettings.interval(ctx), backgroundOn = HubSettings.backgroundOn(ctx),
                guard = HubSettings.moveAlerts(ctx),
                avgCheckMs = checks.takeIf { c -> c.isNotEmpty() }?.map { c -> c.ms }?.average()?.toLong(),
            )
        }
    }

    fun toggleShowPoints() = _ui.update { it.copy(showPoints = !it.showPoints) }
    fun setHeat(on: Boolean) = _ui.update { it.copy(showHeat = on) }

    fun setInterval(min: Int) {
        HubSettings.setInterval(ctx, min)
        PollWorker.restart(ctx)
        viewModelScope.launch { delay(500); loadCheckInfo() }
    }

    fun setBackground(on: Boolean) {
        HubSettings.setBackgroundOn(ctx, on)
        if (on) PollWorker.restart(ctx) else PollWorker.stop(ctx)
        viewModelScope.launch { delay(500); loadCheckInfo() }
    }

    fun select(id: String) {
        stopReplay()
        _ui.update { it.copy(selectedId = id, replayIndex = null) }
        loadHistory()
    }

    fun setRange(r: HubRange) {
        stopReplay()
        _ui.update { it.copy(range = r, replayIndex = null) }
        loadHistory()
    }

    fun setCustomRange(startMs: Long, endMs: Long) {
        stopReplay()
        _ui.update { it.copy(range = HubRange.CUSTOM, customStart = startMs / 1000, customEnd = endMs / 1000 + 86_399, replayIndex = null) }
        loadHistory()
    }

    private fun bounds(u: HubUi): Pair<Long, Long> {
        val now = System.currentTimeMillis() / 1000
        return when (u.range) {
            HubRange.TODAY -> {
                val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis / 1000 to now
            }
            HubRange.CUSTOM -> (u.customStart ?: (now - 86_400)) to (u.customEnd ?: now)
            else -> now - u.range.seconds!! to now
        }
    }

    private fun loadHistory() = viewModelScope.launch {
        val u = _ui.value
        val id = u.selectedId ?: return@launch
        val (s, e) = bounds(u)
        try {
            val pts = FindHub.history(ctx, id, s, e)
            _ui.update { it.copy(history = pts, stats = Trips.stats(pts)) }
        } catch (ex: Exception) {
            toast("Couldn't load history: ${FindHub.friendly(ex)}")
        }
    }

    fun locateNow() = viewModelScope.launch {
        val id = _ui.value.selectedId ?: return@launch
        _ui.update { it.copy(busy = "Asking Find Hub… this can take up to a minute") }
        try {
            val n = FindHub.locateNow(ctx, id)
            toast(if (n > 0) "$n new location${if (n == 1) "" else "s"}" else "No newer location yet. Find Hub only updates when a phone passes the tag.")
            if (n > 0) PollWorker.afterNewLocations(ctx)
        } catch (e: Exception) {
            toast(FindHub.friendly(e))
        }
        _ui.update { it.copy(busy = null) }
        refresh()
    }

    fun checkAllNow() = viewModelScope.launch {
        _ui.update { it.copy(busy = "Checking all tags…") }
        try {
            val n = FindHub.pollAll(ctx)
            HubSettings.recordCheck(ctx, null)
            toast(if (n > 0) "$n new location${if (n == 1) "" else "s"}" else "No new locations")
            if (n > 0) PollWorker.afterNewLocations(ctx)
        } catch (e: Exception) {
            toast(FindHub.friendly(e))
        }
        _ui.update { it.copy(busy = null) }
        refresh()
    }

    fun ring(start: Boolean) = viewModelScope.launch {
        val id = _ui.value.selectedId ?: return@launch
        try {
            FindHub.ring(ctx, id, start)
            _ui.update { it.copy(ringing = start) }
            toast(if (start) "Ring request sent. The tag rings if it's near a phone that can reach it." else "Stopped ringing")
        } catch (e: Exception) {
            toast(FindHub.friendly(e))
        }
    }

    fun rename(name: String) = viewModelScope.launch {
        val id = _ui.value.selectedId ?: return@launch
        runCatching { FindHub.rename(ctx, id, name) }
        refresh()
    }

    // ----- replay -----
    fun setReplayIndex(i: Int?) {
        stopReplay()
        _ui.update { it.copy(replayIndex = i) }
    }

    fun toggleReplay() {
        if (_ui.value.replaying) { stopReplay(); return }
        val n = _ui.value.geoHistory.size
        if (n < 2) { toast("Need at least two locations to replay"); return }
        replayJob = viewModelScope.launch {
            _ui.update { it.copy(replaying = true, replayIndex = it.replayIndex?.takeIf { i -> i < n - 1 } ?: 0) }
            val step = (12_000L / n).coerceIn(60, 600) // whole trip in ~12 s
            while (isActive) {
                delay(step)
                val i = (_ui.value.replayIndex ?: 0) + 1
                if (i >= n) break
                _ui.update { it.copy(replayIndex = i) }
            }
            _ui.update { it.copy(replaying = false) }
        }
    }

    private fun stopReplay() {
        replayJob?.cancel(); replayJob = null
        _ui.update { it.copy(replaying = false) }
    }

    // ----- export -----
    /** Writes CSV or GPX for the current tag and range; returns the file to share. */
    suspend fun export(gpx: Boolean): File? {
        val u = _ui.value
        val id = u.selectedId ?: return null
        val (s, e) = bounds(u)
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val safeName = (u.selected?.name ?: "tag").replace(Regex("[^A-Za-z0-9_-]+"), "_")
        val f = File(dir, "$safeName-${u.range.name.lowercase()}.${if (gpx) "gpx" else "csv"}")
        return try {
            val n = if (gpx) FindHub.exportGpx(ctx, f, id, s, e) else FindHub.exportCsv(ctx, f, id, s, e)
            if (n == 0) { toast("Nothing to export in this range"); null } else f
        } catch (ex: Exception) {
            toast(FindHub.friendly(ex)); null
        }
    }

    // ----- account -----
    fun signOut() = viewModelScope.launch {
        FindHub.signOut(ctx)
        PollWorker.stop(ctx)
        refresh()
    }

    fun importLoginFile(uri: Uri) = viewModelScope.launch {
        val tmp = File(ctx.cacheDir, "secrets-import.json")
        try {
            ctx.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } }
            FindHub.importSecrets(ctx, tmp.absolutePath)
            toast("Login file imported")
            afterSetupStep()
        } catch (e: Exception) {
            toast(FindHub.friendly(e))
        } finally {
            tmp.delete()
        }
    }

    /** Re-reads saved data (no Google request); used for the once-a-minute auto refresh. */
    fun refreshQuiet() = viewModelScope.launch {
        try {
            val devices = FindHub.devices(ctx)
            _ui.update { it.copy(devices = devices) }
            if (!_ui.value.replaying) loadHistory()
            loadCheckInfo()
        } catch (_: Exception) {}
    }

    fun refreshTagList() = viewModelScope.launch {
        _ui.update { it.copy(busy = "Loading tags from Google…") }
        try { FindHub.refreshTagList(ctx); toast("Tag list updated") } catch (e: Exception) { toast(FindHub.friendly(e)) }
        _ui.update { it.copy(busy = null) }
        refresh()
    }

    /** Park & guard: arm movement alerts from the current position, so the stored point doesn't alert. */
    fun setGuard(on: Boolean) = viewModelScope.launch {
        if (on) runCatching { FindHub.newMovements(ctx) }
        HubSettings.setMoveAlerts(ctx, on)
        _ui.update { it.copy(guard = on) }
        toast(if (on) "Parked & guarded: you'll be alerted if a tag moves more than 150 m" else "Guard off")
    }

    fun importCsv(uri: Uri) = viewModelScope.launch {
        val tmp = File(ctx.cacheDir, "import.csv")
        try {
            ctx.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } }
            val n = FindHub.importCsv(ctx, tmp.absolutePath)
            toast("Imported $n location${if (n == 1) "" else "s"} (duplicates skipped)")
        } catch (e: Exception) {
            toast(FindHub.friendly(e))
        } finally { tmp.delete() }
        refresh()
    }

    fun toast(msg: String) = _ui.update { it.copy(message = msg) }
    fun messageShown() = _ui.update { it.copy(message = null) }
}
