package com.aldiian.naruto5.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.aldiian.naruto5.settings.AntiAliasing
import com.aldiian.naruto5.settings.Anisotropy
import com.aldiian.naruto5.settings.AspectMode
import com.aldiian.naruto5.settings.FpsLimit
import com.aldiian.naruto5.settings.GpuDriver
import com.aldiian.naruto5.settings.GraphicsSettings
import com.aldiian.naruto5.settings.PerfProfile
import com.aldiian.naruto5.settings.Preset
import com.aldiian.naruto5.settings.Renderer
import com.aldiian.naruto5.settings.Resolution
import com.aldiian.naruto5.settings.TextureQuality

@Composable
fun SettingsScreen(s: LauncherState, onImportDriver: () -> Unit) {
    val cfg = s.effectiveSettings
    // Any manual change turns the preset into "Custom".
    fun set(n: GraphicsSettings) = s.updateSettings(n.copy(preset = Preset.CUSTOM))
    val widescreen = s.selected?.widescreenPatch == true
    val vulkanPossible = cfg.renderer == Renderer.VULKAN || (cfg.renderer == Renderer.AUTO && s.gpu.isAdreno)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard("Profil pengaturan", subtitle = "Semua opsi di bawah ditulis langsung ke konfigurasi core ARMSX2 (PCSX2-Android.ini) saat game dimulai.") {
            val serial = s.selected?.serial
            ToggleRow(
                "Pengaturan khusus untuk game ini",
                s.editingPerGame,
                { s.setPerGame(it) },
                note = if (serial == null) "Pilih game yang valid terlebih dahulu" else "Disimpan per serial ($serial). Mati = profil global.",
                enabled = serial != null,
            )
            ChoiceRow("Preset grafis", Preset.entries, cfg.preset, { it.label }, { s.updateSettings(cfg.withPreset(it)) },
                note = "Diuji untuk Xiaomi Pad 6 (Snapdragon 870 / Adreno 650): Medium = 720p 60 FPS stabil; High/Ultra butuh perangkat tetap dingin.")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { s.resetSettings() }) { Text("Reset to Default") }
            }
        }

        SectionCard("Rendering") {
            ChoiceRow("Renderer", Renderer.entries, cfg.renderer, { it.label }, { set(cfg.copy(renderer = it)) },
                note = "Otomatis = Vulkan pada Adreno (aturan core ARMSX2). Software sangat berat, hanya untuk debugging.")
            ChoiceRow("Resolusi internal", Resolution.entries, cfg.resolution, { it.label }, { set(cfg.copy(resolution = it)) },
                enabled = cfg.renderer != Renderer.SOFTWARE,
                note = "Upscale dari resolusi asli PS2 (448p). Renderer software selalu native.")
            ChoiceRow("Batas FPS", FpsLimit.entries, cfg.fpsLimit, { it.label }, { set(cfg.copy(fpsLimit = it)) },
                note = "UN5 berjalan 50/60 Hz. 30/45 membatasi frame yang ditampilkan (game tetap kecepatan normal) untuk hemat daya & suhu.")
            ChoiceRow("Kualitas tekstur", TextureQuality.entries, cfg.textureQuality, { it.label }, { set(cfg.copy(textureQuality = it)) },
                enabled = cfg.renderer != Renderer.SOFTWARE)
            ChoiceRow("Kualitas bayangan", listOf("Tidak tersedia"), "Tidak tersedia", { it }, {}, enabled = false,
                note = "Bayangan dirender oleh game PS2 itu sendiri; emulator tidak punya pengaturan bayangan, jadi opsi ini dinonaktifkan.")
            ChoiceRow("Anti-aliasing", AntiAliasing.entries, cfg.antiAliasing, { it.label }, { set(cfg.copy(antiAliasing = it)) },
                note = "MSAA tidak didukung renderer hardware PCSX2 (gunakan resolusi lebih tinggi sebagai gantinya).")
            ChoiceRow("Anisotropic filtering", Anisotropy.entries, cfg.anisotropy, { it.label }, { set(cfg.copy(anisotropy = it)) },
                enabled = cfg.renderer != Renderer.SOFTWARE)
            ToggleRow("V-Sync", cfg.vsync, { set(cfg.copy(vsync = it)) }, note = "Mencegah tearing; menambah sedikit input lag.")
            ToggleRow("Shader cache", cfg.shaderCache, { set(cfg.copy(shaderCache = it)) },
                note = "Menyimpan shader yang sudah dikompilasi di disk agar stutter tidak terulang di sesi berikutnya.")
        }

        SectionCard("Layar") {
            ChoiceRow("Aspect ratio", AspectMode.entries, if (widescreen) cfg.aspect else AspectMode.AUTO, { it.label }, { set(cfg.copy(aspect = it)) },
                enabled = widescreen,
                note = if (widescreen) "16:9/16:10 mengaktifkan patch widescreen 16:9 game (FOV lebar, bukan stretch). 16:10 mengisi layar Pad 6 (sedikit peregangan horizontal ~4%)."
                else "Patch widescreen hanya tersedia untuk SLES-55605; tanpa patch layar dikunci 4:3 agar tidak gepeng.")
            ToggleRow("Fullscreen", cfg.fullscreen, { set(cfg.copy(fullscreen = it)) }, note = "Menyembunyikan status bar & navigation bar selama bermain.")
            ToggleRow("Tampilkan FPS", cfg.showFps, { set(cfg.copy(showFps = it)) }, note = "Overlay OSD dari core.")
        }

        SectionCard("Driver GPU", subtitle = "GPU: ${s.gpu.renderer.ifBlank { "tidak diketahui" }}") {
            val turnipOk = s.gpu.turnipCapable
            ChoiceRow("Driver Vulkan", GpuDriver.entries, if (turnipOk) cfg.gpuDriver else GpuDriver.SYSTEM, { it.label },
                { set(cfg.copy(gpuDriver = it)) }, enabled = turnipOk && vulkanPossible,
                note = when {
                    !turnipOk -> "Turnip hanya untuk GPU Adreno 6xx/7xx; perangkat ini memakai System Driver."
                    !vulkanPossible -> "Driver hanya berlaku untuk renderer Vulkan."
                    else -> "Turnip (Mesa) dimuat lewat libadrenotools menggantikan driver Qualcomm. Direkomendasikan ARMSX2 untuk Adreno 650."
                })
            if (turnipOk) {
                if (s.drivers.isEmpty()) Text("Belum ada driver Turnip terpasang.", style = MaterialTheme.typography.bodySmall)
                s.drivers.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text((if (d.id == cfg.turnipDriverId || (cfg.turnipDriverId.isBlank() && d == s.drivers.first())) "◉ " else "○ ") + d.name + (d.version.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""),
                            modifier = androidx.compose.ui.Modifier.weight(1f))
                        TextButton(onClick = { set(cfg.copy(turnipDriverId = d.id, gpuDriver = GpuDriver.TURNIP)) }) { Text("Pakai") }
                        TextButton(onClick = { s.deleteDriver(d) }) { Text("Hapus") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { s.downloadTurnip() }) { Text("Unduh Turnip ARMSX2") }
                    OutlinedButton(onClick = onImportDriver) { Text("Impor .zip driver") }
                }
            }
        }

        SectionCard("Performa & suhu (Xiaomi Pad 6)") {
            ChoiceRow("Profil performa", PerfProfile.entries, cfg.perfProfile, { it.label }, { set(cfg.copy(perfProfile = it)) },
                note = cfg.perfProfile.description)
            Text("Selalu aktif: JIT ARM64 untuk EE/IOP/VU0/VU1, fastmem, MTVU (VU1 di thread terpisah), refresh layar dikunci 60 Hz untuk frame pacing, shader cache disimpan saat aplikasi di-pause.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
