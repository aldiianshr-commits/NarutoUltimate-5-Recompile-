package com.aldiian.naruto5.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aldiian.naruto5.bios.BiosManager
import com.aldiian.naruto5.core.CoreRuntime
import com.aldiian.naruto5.diag.AppLog
import com.aldiian.naruto5.gpu.GpuInfo
import com.aldiian.naruto5.gpu.TurnipManager
import com.aldiian.naruto5.input.GamepadMapping
import com.aldiian.naruto5.input.TouchLayout
import com.aldiian.naruto5.scan.GameScanner
import com.aldiian.naruto5.scan.ScanResult
import com.aldiian.naruto5.scan.ScannedGame
import com.aldiian.naruto5.settings.GraphicsSettings
import com.aldiian.naruto5.storage.AppPrefs
import com.aldiian.naruto5.storage.Paths
import com.aldiian.naruto5.storage.SaveManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI state for the launcher; all slow work runs on Dispatchers.IO. */
class LauncherState(private val ctx: Context, private val scope: CoroutineScope) {
    val prefs = AppPrefs(ctx)

    var busy by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
    var coreReady by mutableStateOf(false)
        private set
    var allFilesAccess by mutableStateOf(Paths.hasAllFilesAccess())
        private set
    var scan by mutableStateOf<ScanResult?>(null)
        private set
    var selected by mutableStateOf<ScannedGame?>(null)
        private set
    var bios by mutableStateOf<BiosManager.Bios?>(null)
        private set
    var gpu by mutableStateOf(GpuInfo.UNKNOWN)
        private set
    var drivers by mutableStateOf<List<TurnipManager.Driver>>(emptyList())
        private set

    var globalSettings by mutableStateOf(prefs.globalSettings())
        private set
    var gameSettings by mutableStateOf<GraphicsSettings?>(null)
        private set
    var touchLayout by mutableStateOf(prefs.touchLayout())
        private set
    var gamepad by mutableStateOf(prefs.gamepadMapping())
        private set

    var memcards by mutableStateOf<List<SaveManager.SaveFile>>(emptyList())
        private set
    var states by mutableStateOf<List<SaveManager.SaveFile>>(emptyList())
        private set
    var backups by mutableStateOf<List<java.io.File>>(emptyList())
        private set

    /** Settings the Graphics screen edits: per-game when enabled for the selected game. */
    val editingPerGame: Boolean get() = gameSettings != null && selected?.serial != null
    val effectiveSettings: GraphicsSettings get() = gameSettings ?: globalSettings

    private fun work(label: String, block: suspend () -> Unit) {
        scope.launch {
            busy = label
            try { withContext(Dispatchers.IO) { block() } }
            catch (t: Throwable) { AppLog.e("$label gagal", t); message = "$label gagal: ${t.message}" }
            finally { busy = null }
        }
    }

    fun start() = work("Menyiapkan core") {
        gpu = GpuInfo.get(prefs)
        coreReady = CoreRuntime.ensureInitialized(ctx, prefs)
        refreshStorage()
        if (coreReady && allFilesAccess) BiosManager.importFromGameFolder(ctx, prefs)
        bios = if (coreReady) BiosManager.current(ctx, prefs) else null
        drivers = TurnipManager.installed(ctx)
        doScan()
        refreshSaves()
    }

    fun refreshStorage() { allFilesAccess = Paths.hasAllFilesAccess() }

    /**
     * If the emulator process died without a clean exit (native crash, OOM kill), its session
     * marker is still there: tell the player and point at the diagnostics log.
     */
    fun checkAbnormalExit(emuRunning: Boolean) {
        val marker = AppLog.sessionMarker(ctx)
        if (!marker.isFile || emuRunning) return
        marker.delete()
        AppLog.e("Sesi emulator berakhir mendadak (crash native atau dihentikan sistem)")
        message = "Emulator berhenti mendadak. Buka Diagnostik › Ekspor & bagikan log untuk detailnya."
    }

    fun rescan() = work("Memindai game") {
        refreshStorage()
        if (coreReady && allFilesAccess && bios == null) {
            BiosManager.importFromGameFolder(ctx, prefs); bios = BiosManager.current(ctx, prefs)
        }
        doScan()
        refreshSaves()
    }

    private fun doScan() {
        val r = GameScanner.scan(ctx, prefs.gameSource)
        scan = r
        val pick = r.games.firstOrNull { it.location == prefs.selectedGame } ?: r.games.firstOrNull { it.playable } ?: r.games.firstOrNull()
        select(pick)
    }

    fun select(g: ScannedGame?) {
        selected = g
        g?.let { prefs.selectedGame = it.location }
        gameSettings = prefs.gameSettings(g?.serial)
    }

    fun setSource(uri: Uri?) {
        prefs.gameSource = uri?.toString()
        rescan()
    }

    fun importBios(uri: Uri) = work("Impor BIOS") {
        bios = BiosManager.import(ctx, prefs, uri)
        message = "BIOS terpasang: ${bios?.description} ${bios?.version}"
    }

    // ---- settings -------------------------------------------------------------------------
    fun updateSettings(s: GraphicsSettings) {
        val serial = selected?.serial
        if (editingPerGame && serial != null) { gameSettings = s; prefs.saveGameSettings(serial, s) }
        else { globalSettings = s; prefs.saveGlobalSettings(s) }
    }

    fun setPerGame(enabled: Boolean) {
        val serial = selected?.serial ?: return
        if (enabled) { val s = globalSettings; prefs.saveGameSettings(serial, s); gameSettings = s }
        else { prefs.clearGameSettings(serial); gameSettings = null }
    }

    fun resetSettings() = updateSettings(GraphicsSettings.DEFAULT.copy(turnipDriverId = effectiveSettings.turnipDriverId))

    fun downloadTurnip() = work("Unduh driver Turnip") {
        val d = TurnipManager.downloadLatest(ctx)
        drivers = TurnipManager.installed(ctx)
        withContext(Dispatchers.Main) { updateSettings(effectiveSettings.copy(turnipDriverId = d.id)) }
        message = "Driver terpasang: ${d.name}"
    }

    fun importTurnip(uri: Uri) = work("Impor driver") {
        val d = TurnipManager.importZip(ctx, uri)
        drivers = TurnipManager.installed(ctx)
        withContext(Dispatchers.Main) { updateSettings(effectiveSettings.copy(turnipDriverId = d.id)) }
        message = "Driver terpasang: ${d.name}"
    }

    fun deleteDriver(d: TurnipManager.Driver) = work("Hapus driver") {
        TurnipManager.delete(ctx, d); drivers = TurnipManager.installed(ctx)
    }

    // ---- controls -------------------------------------------------------------------------
    fun reloadControls() { touchLayout = prefs.touchLayout(); gamepad = prefs.gamepadMapping() }
    fun updateTouch(l: TouchLayout) { touchLayout = l; prefs.saveTouchLayout(l) }
    fun updateGamepad(m: GamepadMapping) { gamepad = m; prefs.saveGamepadMapping(m) }

    // ---- saves ----------------------------------------------------------------------------
    fun refreshSaves() {
        memcards = SaveManager.memoryCards(ctx); states = SaveManager.saveStates(ctx); backups = SaveManager.backups(ctx)
    }

    fun backup() = work("Backup save") { val f = SaveManager.backup(ctx); refreshSaves(); message = "Backup dibuat: ${f.name}" }
    fun restore(f: java.io.File) = work("Pulihkan save") { val n = SaveManager.restore(ctx, f); refreshSaves(); message = "$n file dipulihkan dari ${f.name}" }
    fun deleteSave(f: java.io.File) = work("Hapus") { SaveManager.delete(f); refreshSaves() }

    /** Readiness checklist for the Play button. */
    fun blockers(): List<String> = buildList {
        if (!CoreRuntime.available) add("Core native tidak termuat")
        if (!allFilesAccess && prefs.gameSource?.startsWith("content://") != true) add("Izin akses folder Naruto5 belum diberikan")
        if (bios == null) add("BIOS PS2 belum dipasang")
        val g = selected
        if (g == null) add("File game belum ditemukan di folder")
        else if (!g.playable) add("File game tidak valid / tidak lengkap")
    }
}
