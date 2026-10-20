package com.aldiian.naruto5.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aldiian.naruto5.core.Backend
import com.aldiian.naruto5.diag.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DiagnosticsScreen(onShare: (java.io.File) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf<List<String>>(emptyList()) }
    var core by remember { mutableStateOf<List<String>?>(null) }
    var crash by remember { mutableStateOf<String?>(null) }
    var onlyImportant by remember { mutableStateOf(true) }

    fun reload() = scope.launch {
        events = withContext(Dispatchers.IO) { AppLog.events(300) }
        crash = withContext(Dispatchers.IO) { AppLog.lastCrash(ctx) }
    }
    LaunchedEffect(Unit) { reload() }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard("Backend eksekusi") {
            Backend.all.forEach { b ->
                StatusLine(if (b.available) true else null, "${b.name} — ${b.kind}", "${b.status}\n${b.description}")
            }
            Text("Perbedaan: emulasi + JIT menerjemahkan kode MIPS game ke ARM64 secara dinamis saat dijalankan dan mengemulasi hardware PS2 (GS, VU, SPU2, IOP). Recompilation statis akan menerjemahkan seluruh executable sebelumnya menjadi kode native — belum ada toolchain yang mampu melakukannya untuk game ini.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard("Perangkat") { AppLog.deviceSummary(ctx).forEach { MonoText(it) } }

        crash?.let { c ->
            SectionCard("Crash terakhir (Java)") {
                MonoText(c.take(4000), color = com.aldiian.naruto5.ui.theme.ErrRed)
                OutlinedButton(onClick = { AppLog.crashFile(ctx).delete(); crash = null }) { Text("Hapus catatan crash") }
            }
        }

        SectionCard("Log") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch { onShare(withContext(Dispatchers.IO) { AppLog.exportReport(ctx) }) } }) { Text("Ekspor & bagikan log") }
                OutlinedButton(onClick = { reload() }) { Text("Muat ulang") }
                OutlinedButton(onClick = { scope.launch { core = withContext(Dispatchers.IO) { AppLog.coreLog(1500) } } }) { Text("Tampilkan log core") }
                OutlinedButton(onClick = { AppLog.clear(); reload() }) { Text("Bersihkan") }
            }
            Text("Event launcher & emulator", style = MaterialTheme.typography.labelLarge)
            if (events.isEmpty()) Text("(kosong)", style = MaterialTheme.typography.bodySmall)
            events.takeLast(150).forEach { MonoText(it, color = if (" E " in it) com.aldiian.naruto5.ui.theme.ErrRed else MaterialTheme.colorScheme.onSurfaceVariant) }
            core?.let { lines ->
                ToggleRow("Hanya baris penting (error/peringatan/ARMSX2)", onlyImportant, { onlyImportant = it })
                Text("Log core (logcat)", style = MaterialTheme.typography.labelLarge)
                val shown = if (onlyImportant) lines.filter { l -> listOf(" E/", " W/", "@@", "Naruto5", "ARMSX2", "PCSX2", "VM ", "GS", "BIOS").any { it in l } } else lines
                shown.takeLast(400).forEach { MonoText(it) }
            }
        }
    }
}
