package com.aldiian.naruto5.diag

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import com.aldiian.naruto5.BuildConfig
import com.aldiian.naruto5.storage.Paths
import kr.co.iefriends.pcsx2.NativeApp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostics. Launcher and emulator (separate processes) append events to one shared file;
 * the native core writes its Console output to logcat, which an app can always read back for
 * its own UID.
 */
object AppLog {
    private const val TAG = "Naruto5"
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    @Volatile private var file: File? = null

    fun init(ctx: Context) {
        val f = File(Paths.logsDir(ctx), "events.log")
        if (f.length() > 512 * 1024) f.writeText(f.readLines().takeLast(1500).joinToString("\n", postfix = "\n"))
        file = f
    }

    fun i(msg: String) { Log.i(TAG, msg); add("I", msg) }
    fun w(msg: String) { Log.w(TAG, msg); add("W", msg) }
    fun e(msg: String, t: Throwable? = null) {
        Log.e(TAG, msg, t)
        add("E", msg + (t?.let { " — ${it.javaClass.simpleName}: ${it.message}" } ?: ""))
    }

    @Synchronized
    private fun add(level: String, msg: String) {
        val proc = if (Process.myPid() == mainPid) "app" else "emu"
        runCatching { file?.appendText("${fmt.format(Date())} $level [$proc] $msg\n") }
    }

    /** Set by the launcher process so lines can be tagged by origin. */
    @Volatile var mainPid: Int = -1

    fun events(maxLines: Int = 800): List<String> = file?.takeIf { it.isFile }?.readLines()?.takeLast(maxLines) ?: emptyList()

    fun clear() { file?.writeText("") }

    /** Native core output (ARMSX2 Console + native crash traces) from logcat. */
    fun coreLog(maxLines: Int = 2000): List<String> = runCatching {
        val proc = ProcessBuilder("logcat", "-d", "-v", "time", "-t", maxLines.toString()).redirectErrorStream(true).start()
        proc.inputStream.bufferedReader().readLines().also { proc.waitFor() }
    }.getOrElse { listOf("(logcat tidak dapat dibaca: ${it.message})") }

    fun deviceSummary(ctx: Context): List<String> = listOf(
        "App: NarutoUltimate 5 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        "Core: ARMSX2 ${BuildConfig.CORE_COMMIT}, lib=${NativeApp.loadedLibrary.ifEmpty { "TIDAK TERMUAT (${NativeApp.loadError})" }}",
        "Perangkat: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})" + if (Build.VERSION.SDK_INT >= 31) ", SoC ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else "",
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ABI ${Build.SUPPORTED_ABIS.joinToString()}",
        "Page size: ${NativeApp.getRuntimePageSize()} B",
        "Akses semua file: ${Paths.hasAllFilesAccess()}",
        "Folder save: ${Paths.saveRoot(ctx).absolutePath}",
        "Data core: ${Paths.dataRoot(ctx).absolutePath}",
    )

    /** Writes a full report (summary + events + core log + last crash) and returns the file. */
    fun exportReport(ctx: Context, extra: List<String> = emptyList()): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val f = File(ctx.cacheDir, "naruto5-log-$stamp.txt")
        f.bufferedWriter().use { w ->
            (deviceSummary(ctx) + extra).forEach { w.appendLine(it) }
            w.appendLine("\n==== Event launcher/emulator ====")
            events(3000).forEach { w.appendLine(it) }
            w.appendLine("\n==== Log core (logcat) ====")
            coreLog(6000).forEach { w.appendLine(it) }
            lastCrash(ctx)?.let { w.appendLine("\n==== Crash terakhir ====\n$it") }
        }
        return f
    }

    fun crashFile(ctx: Context) = File(Paths.logsDir(ctx), "last-crash.txt")
    fun lastCrash(ctx: Context): String? = crashFile(ctx).takeIf { it.isFile }?.readText()
}
