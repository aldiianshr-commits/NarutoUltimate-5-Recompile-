package com.aldiian.naruto5.bios

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.aldiian.naruto5.diag.AppLog
import com.aldiian.naruto5.storage.AppPrefs
import com.aldiian.naruto5.storage.Paths
import kr.co.iefriends.pcsx2.NativeApp
import java.io.File

/**
 * PS2 BIOS handling. The user supplies a BIOS dumped from their own console; it is copied into
 * app-private storage (the core opens it with plain libc I/O) and validated by the core's own
 * BIOS parser (IsBIOS / ROMVER) through NativeApp.getBiosInfoFromFd.
 */
object BiosManager {
    data class Bios(val file: File, val description: String, val region: String, val version: String)

    private val regionNames = mapOf(0 to "Jepang", 1 to "USA", 2 to "Eropa", 4 to "Asia", 6 to "China", 10 to "Bebas region")

    fun describe(file: File): Bios? {
        if (!file.isFile || file.length() < 1L shl 20) return null
        if (NativeApp.hasNoNativeBinary) return null
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                val info = NativeApp.getBiosInfoFromFd(pfd.detachFd()) ?: return null
                Bios(file, info.description, regionNames[info.region] ?: "Region ${info.region}",
                    "v%d.%02d".format((info.version shr 8) and 0xFF, info.version and 0xFF))
            }
        }.getOrNull()
    }

    fun installed(ctx: Context): List<Bios> =
        Paths.biosDir(ctx).listFiles()?.filter { it.isFile }?.mapNotNull { describe(it) } ?: emptyList()

    fun current(ctx: Context, prefs: AppPrefs): Bios? {
        val all = installed(ctx)
        return all.firstOrNull { it.file.name == prefs.biosFile } ?: all.firstOrNull()?.also { prefs.biosFile = it.file.name }
    }

    /** Copies a user-picked file into the BIOS folder; rejects files the core does not accept. */
    fun import(ctx: Context, prefs: AppPrefs, uri: Uri): Bios {
        val name = displayName(ctx, uri) ?: "bios.bin"
        val dest = File(Paths.biosDir(ctx), sanitize(name))
        ctx.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "File BIOS tidak bisa dibuka" }
            dest.outputStream().use { input.copyTo(it) }
        }
        return accept(prefs, dest)
    }

    /** Imports BIOS images found in /Naruto5 or /Naruto5/BIOS (needs all-files access). */
    fun importFromGameFolder(ctx: Context, prefs: AppPrefs): List<Bios> {
        val dirs = listOf(Paths.gameDir(), File(Paths.gameDir(), "BIOS"), File(Paths.gameDir(), "bios"))
        val found = dirs.flatMap { d -> d.listFiles()?.toList() ?: emptyList() }
            .filter { it.isFile && it.length() in (2L shl 20)..(16L shl 20) && it.extension.lowercase() in setOf("bin", "rom", "rom0", "") }
        return found.mapNotNull { src ->
            val dest = File(Paths.biosDir(ctx), sanitize(src.name))
            runCatching {
                if (!dest.isFile || dest.length() != src.length()) src.copyTo(dest, overwrite = true)
                accept(prefs, dest)
            }.onFailure { AppLog.w("BIOS ditolak: ${src.path} — ${it.message}") }.getOrNull()
        }
    }

    private fun accept(prefs: AppPrefs, dest: File): Bios {
        val bios = describe(dest)
        if (bios == null) {
            dest.delete()
            error("File bukan BIOS PS2 yang valid menurut core")
        }
        prefs.biosFile = dest.name
        AppLog.i("BIOS dipasang: ${bios.description} ${bios.version} (${bios.region})")
        return bios
    }

    fun delete(ctx: Context, prefs: AppPrefs, bios: Bios) {
        bios.file.delete()
        if (prefs.biosFile == bios.file.name) prefs.biosFile = null
    }

    private fun sanitize(n: String) = n.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80)

    private fun displayName(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()
}
