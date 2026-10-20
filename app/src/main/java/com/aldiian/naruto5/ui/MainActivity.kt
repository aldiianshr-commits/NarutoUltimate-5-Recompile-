package com.aldiian.naruto5.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.aldiian.naruto5.diag.AppLog
import com.aldiian.naruto5.emu.EmulationActivity
import com.aldiian.naruto5.ui.theme.NarutoTheme

class MainActivity : ComponentActivity() {

    /** One-shot gamepad key capture used by the button-mapping dialog. */
    private var keyCapture: ((Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { NarutoTheme { Launcher() } }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val cap = keyCapture
        val fromPad = event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK || KeyEvent.isGamepadButton(event.keyCode)
        if (cap != null && fromPad && event.keyCode != KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) { keyCapture = null; cap(event.keyCode) }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private enum class Tab(val label: String, val icon: String) {
        HOME("Beranda", "🏠"), GRAPHICS("Grafis", "🖥"), CONTROLS("Kontrol", "🎮"), SAVES("Save", "💾"), DIAG("Diagnostik", "🩺")
    }

    @Composable
    private fun Launcher() {
        val scope = rememberCoroutineScope()
        val state = remember { LauncherState(applicationContext, scope) }
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val snack = remember { SnackbarHostState() }
        val wide = LocalConfiguration.current.screenWidthDp >= 720

        LaunchedEffect(Unit) { state.start() }
        LaunchedEffect(state.message) { state.message?.let { snack.showSnackbar(it); state.message = null } }

        // Refresh storage permission / controls when returning from Settings or the editor.
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val obs = LifecycleEventObserver { _, e ->
                if (e == Lifecycle.Event.ON_RESUME) {
                    val had = state.allFilesAccess
                    state.refreshStorage(); state.reloadControls(); state.refreshSaves()
                    if (!had && state.allFilesAccess) state.rescan()
                    val am = getSystemService(android.app.ActivityManager::class.java)
                    val emuAlive = am?.runningAppProcesses?.any { it.processName == "$packageName:emu" } == true
                    state.checkAbnormalExit(emuAlive)
                }
            }
            owner.lifecycle.addObserver(obs)
            onDispose { owner.lifecycle.removeObserver(obs) }
        }

        val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                state.setSource(uri)
            }
        }
        val pickBios = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { state.importBios(it) } }
        val pickDriver = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { state.importTurnip(it) } }
        val legacyPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { state.rescan() }

        fun grantAccess() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
                }.onFailure { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            } else legacyPerm.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }

        fun play() {
            val g = state.selected ?: return
            AppLog.i("PLAY: ${g.title} [${g.serial}] ${g.location}")
            startActivity(EmulationActivity.intent(this, g.location, g.serial, g.title, g.widescreenPatch))
        }

        fun share(f: java.io.File) {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Bagikan log"))
        }

        val content: @Composable (Modifier) -> Unit = { mod ->
            Column(mod.fillMaxSize()) {
                state.busy?.let {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 980.dp).padding(20.dp)) {
                        when (Tab.entries[tab]) {
                            Tab.HOME -> HomeScreen(state, ::play, ::grantAccess, { pickFolder.launch(null) }, { pickBios.launch(arrayOf("*/*")) })
                            Tab.GRAPHICS -> SettingsScreen(state) { pickDriver.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
                            Tab.CONTROLS -> ControlsScreen(state,
                                onEditLayout = { startActivity(Intent(this@MainActivity, LayoutEditorActivity::class.java)) },
                                startCapture = { cb -> keyCapture = cb },
                                stopCapture = { keyCapture = null })
                            Tab.SAVES -> SavesScreen(state)
                            Tab.DIAG -> DiagnosticsScreen(::share)
                        }
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.background) {
            if (wide) {
                Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
                    Row(Modifier.padding(pad)) {
                        NavigationRail(Modifier.safeDrawingPadding()) {
                            Tab.entries.forEachIndexed { i, t ->
                                NavigationRailItem(selected = tab == i, onClick = { tab = i }, icon = { Text(t.icon) }, label = { Text(t.label) })
                            }
                        }
                        content(Modifier)
                    }
                }
            } else {
                Scaffold(
                    snackbarHost = { SnackbarHost(snack) },
                    bottomBar = {
                        NavigationBar {
                            Tab.entries.forEachIndexed { i, t ->
                                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(t.icon) }, label = { Text(t.label) })
                            }
                        }
                    },
                ) { pad -> content(Modifier.padding(pad)) }
            }
        }
    }
}
