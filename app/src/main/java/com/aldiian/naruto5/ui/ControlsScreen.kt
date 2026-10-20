package com.aldiian.naruto5.ui

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aldiian.naruto5.input.GamepadInput
import com.aldiian.naruto5.input.GamepadMapping
import com.aldiian.naruto5.input.PadKey
import kotlinx.coroutines.delay

/**
 * @param startCapture registers a one-shot key listener on the activity; it is called with the
 *        Android keycode of the next gamepad button pressed.
 */
@Composable
fun ControlsScreen(s: LauncherState, onEditLayout: () -> Unit, startCapture: ((Int) -> Unit) -> Unit, stopCapture: () -> Unit) {
    var binding by remember { mutableStateOf<PadKey?>(null) }
    var pads by remember { mutableStateOf(GamepadInput.connectedPads().map { it.name }) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { delay(2000); pads = GamepadInput.connectedPads().map { it.name }; tick++ }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard("Kontrol layar sentuh", subtitle = "D-pad, dua analog, tombol wajah, L1/L2/R1/R2, L3/R3, Start/Select. Multi-touch; geser antar tombol didukung.") {
            val l = s.touchLayout
            ToggleRow("Aktifkan kontrol layar", l.enabled, { s.updateTouch(l.copy(enabled = it)) },
                note = "Otomatis disembunyikan saat gamepad terhubung/ditekan.")
            Text("Transparansi: ${(l.opacity * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
            Slider(value = l.opacity, onValueChange = { s.updateTouch(l.copy(opacity = it)) }, valueRange = 0.1f..1f)
            Text("Ukuran semua tombol: ${(l.globalScale * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
            Slider(value = l.globalScale, onValueChange = { s.updateTouch(l.copy(globalScale = it)) }, valueRange = 0.5f..2f)
            ToggleRow("Getar saat tombol ditekan", l.hapticFeedback, { s.updateTouch(l.copy(hapticFeedback = it)) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEditLayout) { Text("Atur posisi & ukuran tombol") }
                OutlinedButton(onClick = { s.updateTouch(com.aldiian.naruto5.input.TouchLayout()) }) { Text("Reset tata letak") }
            }
        }

        SectionCard("Gamepad Bluetooth / USB", subtitle = if (pads.isEmpty()) "Tidak ada gamepad terhubung" else "Terhubung: " + pads.joinToString()) {
            val m = s.gamepad
            ToggleRow("Trigger analog (L2/R2 bertekanan)", m.analogTriggers, { s.updateGamepad(m.copy(analogTriggers = it)) },
                note = "Membaca sumbu trigger analog gamepad sebagai tombol bertekanan PS2.")
            ToggleRow("Getaran (rumble)", m.rumble, { s.updateGamepad(m.copy(rumble = it)) },
                note = "Motor DualShock 2 dari game diteruskan ke motor gamepad (atau perangkat bila gamepad tidak punya).")
            Text("Analog kiri/kanan dan hat D-pad dibaca otomatis dari sumbu gamepad. Dalam game: START + SELECT bersamaan = menu jeda.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            PadKey.bindable.forEach { key ->
                val bound = m.buttons.filterValues { it == key }.keys.firstOrNull()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(key.label, modifier = Modifier.width(80.dp), style = MaterialTheme.typography.titleSmall)
                    Text(bound?.let { KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_") } ?: "— tidak dipetakan —",
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        binding = key
                        startCapture { code -> s.updateGamepad(s.gamepad.bind(code, key)); binding = null }
                    }) { Text("Ubah") }
                }
            }
            OutlinedButton(onClick = { s.updateGamepad(GamepadMapping()) }) { Text("Reset pemetaan gamepad") }
        }
    }

    binding?.let { key ->
        AlertDialog(
            onDismissRequest = { stopCapture(); binding = null },
            title = { Text("Petakan $key") },
            text = { Text("Tekan tombol pada gamepad yang ingin dipakai sebagai ${key.label}.") },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { stopCapture(); binding = null }) { Text("Batal") } },
        )
    }
}
