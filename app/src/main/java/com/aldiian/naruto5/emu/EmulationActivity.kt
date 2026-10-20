package com.aldiian.naruto5.emu

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aldiian.naruto5.core.CoreRuntime
import com.aldiian.naruto5.diag.AppLog
import com.aldiian.naruto5.input.GamepadInput
import com.aldiian.naruto5.input.LayoutEditorPanel
import com.aldiian.naruto5.input.TouchControllerView
import com.aldiian.naruto5.settings.CoreMapping
import com.aldiian.naruto5.settings.GraphicsSettings
import com.aldiian.naruto5.settings.PerfProfile
import com.aldiian.naruto5.storage.AppPrefs
import kr.co.iefriends.pcsx2.NativeApp
import kotlin.concurrent.thread

/**
 * Runs the game. Lives in its own process (":emu") so leaving the game can tear the whole VM
 * down with the process, exactly like the ARMSX2 frontend does, without closing the launcher.
 */
class EmulationActivity : ComponentActivity(), SurfaceHolder.Callback, NativeApp.Listener {

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_SERIAL = "serial"
        const val EXTRA_TITLE = "title"
        const val EXTRA_WIDESCREEN = "widescreen"

        fun intent(ctx: Context, path: String, serial: String?, title: String, widescreen: Boolean) =
            Intent(ctx, EmulationActivity::class.java)
                .putExtra(EXTRA_PATH, path).putExtra(EXTRA_SERIAL, serial)
                .putExtra(EXTRA_TITLE, title).putExtra(EXTRA_WIDESCREEN, widescreen)
    }

    private lateinit var prefs: AppPrefs
    private lateinit var settings: GraphicsSettings
    private lateinit var surfaceView: SurfaceView
    private lateinit var touch: TouchControllerView
    private lateinit var root: FrameLayout
    private lateinit var status: TextView
    private lateinit var menuButton: TextView
    private var menu: View? = null
    private var editor: View? = null
    private lateinit var gamepad: GamepadInput

    private var path = ""
    private var serial: String? = null
    private var widescreen = false
    private var emuThread: Thread? = null
    @Volatile private var vmRunning = false
    @Volatile private var exiting = false
    private var fpsCapApplied = false
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPrefs(this)
        path = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
        serial = intent.getStringExtra(EXTRA_SERIAL)
        widescreen = intent.getBooleanExtra(EXTRA_WIDESCREEN, false)
        settings = prefs.effectiveSettings(serial)
        gamepad = GamepadInput(prefs.gamepadMapping())

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyWindowMode()
        buildUi()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    editor != null -> closeEditor()
                    menu != null -> closeMenu()
                    else -> openMenu()
                }
            }
        })

        if (!CoreRuntime.available) {
            showError("Core emulator (lib${BuildConfig_EMUCORE()}.so) tidak termuat:\n${NativeApp.loadError}")
            return
        }
        NativeApp.listener = this
        status.text = "Menyiapkan core…"
        thread(name = "n5-init") {
            val ok = runCatching {
                CoreRuntime.ensureInitialized(this, prefs).also {
                    if (it) CoreRuntime.applySettings(this, settings, serial, widescreen)
                }
            }.onFailure { AppLog.e("Inisialisasi core gagal", it) }.getOrDefault(false)
            ui.post {
                if (!ok) showError("Inisialisasi core gagal. Lihat Diagnostik.")
                else { status.text = "Memuat ${intent.getStringExtra(EXTRA_TITLE) ?: "game"}…"; maybeStartVm() }
            }
        }
    }

    private fun BuildConfig_EMUCORE() = com.aldiian.naruto5.BuildConfig.EMUCORE_LIB

    private fun currentDisplay(): android.view.Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else @Suppress("DEPRECATION") windowManager.defaultDisplay

    // ---- window / display -----------------------------------------------------------------
    private fun applyWindowMode() {
        WindowCompat.setDecorFitsSystemWindows(window, !settings.fullscreen)
        val c = WindowInsetsControllerCompat(window, window.decorView)
        if (settings.fullscreen) {
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else c.show(WindowInsetsCompat.Type.systemBars())

        // Pick a display mode at 60 Hz (Pad 6 panel runs up to 144 Hz): PS2 games are 59.94/50 Hz,
        // and a matching refresh rate gives even frame pacing and saves power.
        currentDisplay()?.let { d ->
            val cur = d.mode
            val best = d.supportedModes.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                .minByOrNull { kotlin.math.abs(it.refreshRate - 60f) }
            if (best != null) window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
        }
        if (settings.perfProfile == PerfProfile.BATTERY) {
            runCatching { window.setSustainedPerformanceMode(true) }
        }
    }

    private fun buildUi() {
        val dp = resources.displayMetrics.density
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(this)
        root.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))

        touch = TouchControllerView(this).apply {
            layout = prefs.touchLayout()
            visibility = if (layout.enabled && GamepadInput.connectedPads().isEmpty()) View.VISIBLE else View.GONE
            onEmptyTap = { flashMenuButton() }
        }
        root.addView(touch, FrameLayout.LayoutParams(-1, -1))

        menuButton = TextView(this).apply {
            text = "☰"; textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(110, 0, 0, 0)) }
            setOnClickListener { openMenu() }
        }
        root.addView(menuButton, FrameLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = (8 * dp).toInt() })

        status = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 15f; gravity = Gravity.CENTER
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
        }
        root.addView(status, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        setContentView(root)
    }

    private fun flashMenuButton() {
        menuButton.alpha = 1f
        menuButton.animate().alpha(0.35f).setStartDelay(2500).setDuration(600).start()
    }

    // ---- surface & VM ---------------------------------------------------------------------
    @Volatile private var surfaceReady = false

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (!CoreRuntime.available) return
        NativeApp.onNativeSurfaceCreated()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { holder.surface.setFrameRate(60f, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE) }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (!CoreRuntime.available) return
        NativeApp.onNativeSurfaceChanged(holder.surface, width, height)
        NativeApp.setDisplayRefreshRate(currentDisplay()?.refreshRate ?: 60f)
        surfaceReady = true
        maybeStartVm()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        if (CoreRuntime.available) NativeApp.onNativeSurfaceDestroyed()
    }

    private fun maybeStartVm() {
        if (emuThread != null || !surfaceReady || !CoreRuntime.initialized || exiting) return
        AppLog.i("Boot VM: $path (serial=$serial, renderer=${settings.renderer.label})")
        runCatching { AppLog.sessionMarker(this).writeText("${System.currentTimeMillis()}\n$path\n") }
        emuThread = thread(name = "n5-emu", priority = Thread.MAX_PRIORITY) {
            val ok = runCatching { NativeApp.runVMThread(path) }.onFailure { AppLog.e("runVMThread exception", it) }.getOrDefault(false)
            val err = runCatching { NativeApp.getLastBootError() }.getOrDefault("")
            AppLog.i("VM selesai: ok=$ok err=${err.ifBlank { "-" }}")
            ui.post { onVmExited(ok, err) }
        }
        pollBoot()
    }

    /** Waits for the first frames, then applies settings that need a live VM (FPS cap). */
    private fun pollBoot(attempt: Int = 0) {
        ui.postDelayed({
            if (exiting) return@postDelayed
            val active = runCatching { NativeApp.hasActiveVM() }.getOrDefault(false)
            val fps = runCatching { NativeApp.getFPS() }.getOrDefault(0f)
            if (active && fps > 0f) {
                vmRunning = true
                status.text = ""
                applyFpsCap()
                flashMenuButton()
                AppLog.i("Game berjalan (${"%.1f".format(fps)} FPS awal)")
            } else if (attempt < 240) {
                pollBoot(attempt + 1)
            }
        }, 500)
    }

    private fun applyFpsCap() {
        val cap = CoreMapping.fpsCap(settings)
        NativeApp.setFpsCap(cap)
        fpsCapApplied = true
    }

    private fun onVmExited(ok: Boolean, err: String) {
        vmRunning = false
        AppLog.sessionMarker(this).delete()
        if (exiting) return
        if (!ok || err.isNotBlank()) {
            showError("Game gagal dijalankan.\n\n${err.ifBlank { "Core menghentikan VM saat boot (periksa BIOS dan file game)." }}")
        } else finishAndKill()
    }

    private fun showError(msg: String) {
        AppLog.e(msg)
        touch.visibility = View.GONE
        status.text = ""
        val dp = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt(), (20 * dp).toInt())
            background = GradientDrawable().apply { setColor(Color.argb(235, 30, 16, 16)); cornerRadius = 18 * dp }
        }
        box.addView(TextView(this).apply { text = msg; setTextColor(Color.WHITE); textSize = 15f })
        box.addView(Button(this).apply { text = "Kembali ke launcher"; setOnClickListener { finishAndKill() } })
        root.addView(ScrollView(this).apply { addView(box) }, FrameLayout.LayoutParams((520 * dp).toInt(), -2, Gravity.CENTER))
    }

    // ---- pause menu -----------------------------------------------------------------------
    private fun openMenu() {
        if (menu != null || editor != null) return
        if (vmRunning) NativeApp.pause()
        touch.releaseAll(); gamepad.releaseAll()
        val dp = resources.displayMetrics.density
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (16 * dp).toInt())
            background = GradientDrawable().apply { setColor(Color.argb(235, 14, 18, 30)); cornerRadius = 20 * dp }
        }
        panel.addView(TextView(this).apply {
            text = (intent.getStringExtra(EXTRA_TITLE) ?: "Game") + "\n" + stats()
            setTextColor(Color.WHITE); textSize = 14f
        })
        fun btn(label: String, action: () -> Unit) = panel.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        btn("▶  Lanjutkan") { closeMenu() }
        val slots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (s in 1..3) slots.addView(Button(this).apply { text = "Simpan $s"; setOnClickListener { saveState(s) } })
        panel.addView(slots)
        val loads = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (s in 1..3) loads.addView(Button(this).apply { text = "Muat $s"; setOnClickListener { loadState(s) } })
        panel.addView(loads)
        btn(if (touch.visibility == View.VISIBLE) "Sembunyikan kontrol layar" else "Tampilkan kontrol layar") {
            touch.visibility = if (touch.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            closeMenu()
        }
        btn("Edit tata letak kontrol") { closeMenu(resume = false); openEditor() }
        btn(if (settings.showFps) "Sembunyikan FPS" else "Tampilkan FPS") {
            settings = settings.copy(showFps = !settings.showFps); NativeApp.osdShowFPS(settings.showFps); closeMenu()
        }
        btn("⏏  Keluar dari game") { exitGame() }
        val scroll = ScrollView(this).apply { addView(panel) }
        menu = scroll
        root.addView(scroll, FrameLayout.LayoutParams((380 * dp).toInt(), -2, Gravity.CENTER))
    }

    private fun stats(): String = if (!vmRunning) "VM belum berjalan" else runCatching {
        "FPS %.0f · kecepatan %.0f%% · CPU %.0f%% · GS %.0f%% · %s".format(
            NativeApp.getFPS(), NativeApp.getEmuSpeedPercent(), NativeApp.getCpuThreadUsage(), NativeApp.getGsThreadUsage(),
            if (NativeApp.isHardwareRenderer()) "renderer hardware" else "renderer software")
    }.getOrDefault("")

    private fun closeMenu(resume: Boolean = true) {
        menu?.let { root.removeView(it) }
        menu = null
        if (resume && vmRunning) NativeApp.resume()
    }

    private fun saveState(slot: Int) {
        thread {
            val ok = runCatching { NativeApp.saveStateToSlot(slot) }.getOrDefault(false)
            AppLog.i("Save state slot $slot: $ok")
            ui.post { Toast.makeText(this, if (ok) "Tersimpan di slot $slot" else "Gagal menyimpan slot $slot", Toast.LENGTH_SHORT).show(); closeMenu() }
        }
    }

    private fun loadState(slot: Int) {
        thread {
            val ok = runCatching { NativeApp.loadStateFromSlot(slot) }.getOrDefault(false)
            AppLog.i("Load state slot $slot: $ok")
            ui.post { Toast.makeText(this, if (ok) "Slot $slot dimuat" else "Slot $slot kosong / gagal", Toast.LENGTH_SHORT).show(); closeMenu() }
        }
    }

    private fun openEditor() {
        touch.visibility = View.VISIBLE
        touch.editMode = true
        val dp = resources.displayMetrics.density
        val panel = LayoutEditorPanel(this, touch) { l ->
            prefs.saveTouchLayout(l)
            closeEditor()
        }
        editor = panel
        root.addView(panel, FrameLayout.LayoutParams((420 * dp).toInt(), -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = (12 * dp).toInt() })
    }

    private fun closeEditor() {
        editor?.let { root.removeView(it) }
        editor = null
        touch.editMode = false
        prefs.saveTouchLayout(touch.layout)
        if (vmRunning) NativeApp.resume()
    }

    private fun exitGame() {
        if (exiting) return
        exiting = true
        status.text = "Menyimpan & menutup…"
        closeMenu(resume = false)
        thread {
            // shutdown() flushes memory cards and waits (bounded) for the VM to stop.
            runCatching { NativeApp.shutdown() }
            emuThread?.join(3000)
            ui.post { finishAndKill() }
        }
    }

    private fun finishAndKill() {
        exiting = true
        AppLog.sessionMarker(this).delete()
        finish()
        ui.postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()) }, 400)
    }

    // ---- lifecycle ------------------------------------------------------------------------
    override fun onPause() {
        super.onPause()
        if (CoreRuntime.available && vmRunning) {
            NativeApp.setAppBackgrounded(true)
            NativeApp.pause()
            NativeApp.flushShaderCache()
        }
        touch.releaseAll(); gamepad.releaseAll()
    }

    override fun onResume() {
        super.onResume()
        if (CoreRuntime.available && vmRunning) {
            NativeApp.setAppBackgrounded(false)
            if (menu == null && editor == null) NativeApp.resume()
        }
        if (settings.fullscreen) WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onDestroy() {
        NativeApp.listener = null
        if (CoreRuntime.available && !exiting) runCatching { NativeApp.shutdown() }
        super.onDestroy()
    }

    // ---- input ----------------------------------------------------------------------------
    private val padDown = HashSet<Int>()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // START + SELECT together opens the pause menu (many pads never deliver Home/Mode).
        if (KeyEvent.isGamepadButton(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_DOWN) padDown += event.keyCode else padDown -= event.keyCode
            if (event.action == KeyEvent.ACTION_DOWN && menu == null && editor == null &&
                KeyEvent.KEYCODE_BUTTON_START in padDown && KeyEvent.KEYCODE_BUTTON_SELECT in padDown) {
                padDown.clear(); gamepad.releaseAll(); openMenu(); return true
            }
        }
        if (menu == null && editor == null && gamepad.onKey(event)) {
            if (touch.visibility == View.VISIBLE && event.action == KeyEvent.ACTION_DOWN) touch.visibility = View.GONE
            return true
        }
        // Gamepad "Mode/Home-like" buttons open the pause menu.
        if (event.action == KeyEvent.ACTION_UP && (event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE)) {
            if (menu == null) openMenu() else closeMenu(); return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (menu == null && editor == null && gamepad.onMotion(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    // ---- callbacks from the core ----------------------------------------------------------
    override fun onVmPaused(paused: Boolean) {
        ui.post { if (!paused && !fpsCapApplied && vmRunning) applyFpsCap() }
    }

    override fun onPadRumble(pad: Int, largeMotor: Int, smallMotor: Int) {
        if (pad == 0) gamepad.rumble(this, largeMotor, smallMotor)
    }
}
