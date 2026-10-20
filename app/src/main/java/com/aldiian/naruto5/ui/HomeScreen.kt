package com.aldiian.naruto5.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aldiian.naruto5.R
import com.aldiian.naruto5.core.Backend
import com.aldiian.naruto5.scan.KnownGames
import com.aldiian.naruto5.scan.Severity
import com.aldiian.naruto5.storage.Paths

@Composable
fun HomeScreen(
    s: LauncherState,
    onPlay: () -> Unit,
    onGrantAccess: () -> Unit,
    onPickFolder: () -> Unit,
    onPickBios: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.naruto5_logo), contentDescription = null,
                modifier = Modifier.size(112.dp).clip(RoundedCornerShape(26.dp)))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("NarutoUltimate 5", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Naruto Shippuden: Ultimate Ninja 5 (PS2) untuk Android ARM64",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Backend: ${Backend.active.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }

        val blockers = s.blockers()
        Button(
            onClick = onPlay,
            enabled = blockers.isEmpty() && s.busy == null,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) { Text("▶  PLAY", fontSize = 22.sp, fontWeight = FontWeight.Black) }
        if (blockers.isNotEmpty()) {
            Text("Belum bisa main: " + blockers.joinToString(" · "), color = com.aldiian.naruto5.ui.theme.WarnAmber,
                style = MaterialTheme.typography.bodySmall)
        }

        SectionCard("Status") {
            StatusLine(Backend.Armsx2Jit.available, "Core emulator", Backend.Armsx2Jit.status)
            StatusLine(if (s.allFilesAccess) true else null, "Akses folder ${Paths.GAME_DIR}",
                if (s.allFilesAccess) "Diizinkan" else "Belum diizinkan — pakai tombol di bawah, atau pilih folder via SAF")
            StatusLine(s.bios != null, "BIOS PS2",
                s.bios?.let { "${it.description} ${it.version} · ${it.region} · ${it.file.name}" }
                    ?: "Letakkan BIOS dump dari konsol Anda di ${Paths.GAME_DIR}/BIOS atau impor manual")
            val g = s.selected
            StatusLine(g?.playable ?: false, "File game", g?.let { "${it.displayName} · ${formatSize(it.sizeBytes)}" } ?: "Tidak ada image di ${s.scan?.source ?: Paths.GAME_DIR}")
            StatusLine(s.gpu.renderer.isNotBlank(), "GPU", s.gpu.renderer.ifBlank { "Tidak terdeteksi" } + if (s.gpu.renderer.isNotBlank()) " · ${s.gpu.version}" else "")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!s.allFilesAccess) FilledTonalButton(onClick = onGrantAccess) { Text("Beri akses semua file") }
                OutlinedButton(onClick = onPickFolder) { Text("Pilih folder (SAF)") }
                OutlinedButton(onClick = onPickBios) { Text("Impor BIOS") }
                OutlinedButton(onClick = { s.rescan() }) { Text("Pindai ulang") }
            }
            if (s.prefs.gameSource != null) {
                Text("Sumber: ${s.prefs.gameSource}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { s.setSource(null) }) { Text("Kembali ke folder default") }
            }
        }

        s.selected?.let { g ->
            SectionCard("Game terdeteksi", subtitle = g.location) {
                val rel = g.release
                Text(g.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(buildString {
                    append("Serial: ${g.serial ?: "?"}")
                    rel?.let { append(" · ${it.region}") }
                    append(" · Format: ${g.info.format.label}")
                }, style = MaterialTheme.typography.bodyMedium)
                when (g.match) {
                    KnownGames.Match.TARGET -> StatusLine(true, "Naruto Shippuden: Ultimate Ninja 5 (PAL) — target utama", rel?.note)
                    KnownGames.Match.REGIONAL_VARIANT -> StatusLine(null, "Varian regional UN5", rel?.note)
                    KnownGames.Match.OTHER_GAME -> StatusLine(null, "Game PS2 lain", "Bisa dijalankan core, tetapi launcher ini dioptimalkan untuk UN5.")
                    KnownGames.Match.UNKNOWN -> StatusLine(false, "Serial tidak dikenali")
                }
                StatusLine(if (g.compat >= 5) true else null, "Kompatibilitas GameDB: ${KnownGames.compatLabel(g.compat)}",
                    "Perbaikan per-game dari GameDB PCSX2 diterapkan otomatis oleh core saat boot.")
                StatusLine(g.widescreenPatch, "Patch widescreen 16:9", if (g.widescreenPatch) "Tersedia (pnach ElHecht dalam patches.zip core)" else "Tidak tersedia untuk serial ini — aspek dikunci 4:3")
                Text("Validasi image", style = MaterialTheme.typography.labelLarge)
                g.info.findings.forEach { f ->
                    StatusLine(when (f.severity) { Severity.OK -> true; Severity.WARNING -> null; Severity.ERROR -> false }, f.message)
                }
                Text("Kompilasi: tidak ada langkah konversi offline — kode MIPS game diterjemahkan ke ARM64 oleh JIT saat runtime (lihat Diagnostik › Backend).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val games = s.scan?.games.orEmpty()
        if (games.size > 1) {
            SectionCard("Image lain di folder") {
                games.forEach { g ->
                    Row(Modifier.fillMaxWidth().clickable { s.select(g) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (g == s.selected) "◉" else "○", modifier = Modifier.width(24.dp), color = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(g.displayName, style = MaterialTheme.typography.bodyMedium)
                            Text("${g.serial ?: "?"} · ${formatSize(g.sizeBytes)} · ${if (g.playable) "valid" else "bermasalah"}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        s.scan?.skipped?.takeIf { it.isNotEmpty() }?.let { sk ->
            Text("Dilewati: " + sk.joinToString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        s.scan?.error?.let { Text(it, color = com.aldiian.naruto5.ui.theme.ErrRed, style = MaterialTheme.typography.bodySmall) }
    }
}
