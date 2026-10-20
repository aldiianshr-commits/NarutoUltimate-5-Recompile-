package com.aldiian.naruto5.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aldiian.naruto5.storage.Paths
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun SavesScreen(s: LauncherState) {
    val ctx = LocalContext.current
    var confirmRestore by remember { mutableStateOf<File?>(null) }
    var confirmDelete by remember { mutableStateOf<File?>(null) }
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard("Folder save", subtitle = Paths.saveRoot(ctx).absolutePath) {
            if (!Paths.usingPublicSaveDir(ctx)) {
                StatusLine(null, "Belum memakai ${Paths.SAVE_DIR}", "Beri akses semua file di Beranda agar save tersimpan di folder publik (aman saat aplikasi dihapus).")
            } else StatusLine(true, "Save tersimpan di folder publik", "memcards/ = memory card PS2 · states/ = save state · backups/ = arsip zip")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { s.backup() }) { Text("Backup sekarang") }
                OutlinedButton(onClick = { s.refreshSaves() }) { Text("Muat ulang") }
            }
        }

        SectionCard("Memory card (${s.memcards.size})", subtitle = "Dibuat otomatis oleh core saat game pertama kali menyimpan.") {
            if (s.memcards.isEmpty()) Text("Belum ada memory card.", style = MaterialTheme.typography.bodySmall)
            s.memcards.forEach { f -> FileRow(f.file, "${formatSize(f.size)} · ${df.format(Date(f.modified))}") { confirmDelete = f.file } }
        }

        SectionCard("Save state (${s.states.size})", subtitle = "Dibuat dari menu jeda dalam game (slot 1–3).") {
            if (s.states.isEmpty()) Text("Belum ada save state.", style = MaterialTheme.typography.bodySmall)
            s.states.forEach { f -> FileRow(f.file, "${formatSize(f.size)} · ${df.format(Date(f.modified))}") { confirmDelete = f.file } }
        }

        SectionCard("Backup (${s.backups.size})", subtitle = "Memulihkan backup selalu membuat backup pengaman dari kondisi sekarang terlebih dulu.") {
            if (s.backups.isEmpty()) Text("Belum ada backup.", style = MaterialTheme.typography.bodySmall)
            s.backups.forEach { f ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, style = MaterialTheme.typography.bodyMedium)
                        Text("${formatSize(f.length())} · ${df.format(Date(f.lastModified()))}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { confirmRestore = f }) { Text("Pulihkan") }
                    TextButton(onClick = { confirmDelete = f }) { Text("Hapus") }
                }
            }
        }
    }

    confirmRestore?.let { f ->
        AlertDialog(onDismissRequest = { confirmRestore = null },
            title = { Text("Pulihkan ${f.name}?") },
            text = { Text("Memory card dan save state saat ini akan ditimpa. Backup pengaman otomatis dibuat lebih dulu.") },
            confirmButton = { TextButton(onClick = { s.restore(f); confirmRestore = null }) { Text("Pulihkan") } },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Batal") } })
    }
    confirmDelete?.let { f ->
        AlertDialog(onDismissRequest = { confirmDelete = null },
            title = { Text("Hapus ${f.name}?") },
            text = { Text("Tindakan ini tidak bisa dibatalkan.") },
            confirmButton = { TextButton(onClick = { s.deleteSave(f); confirmDelete = null }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Batal") } })
    }
}

@Composable
private fun FileRow(f: File, detail: String, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(f.name, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onDelete) { Text("Hapus") }
    }
}
