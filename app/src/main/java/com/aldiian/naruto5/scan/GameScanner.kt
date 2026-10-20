package com.aldiian.naruto5.scan

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import com.aldiian.naruto5.diag.AppLog
import com.aldiian.naruto5.storage.Paths
import kr.co.iefriends.pcsx2.NativeApp
import java.io.File
import java.io.FileInputStream

/** A disc image found in the game folder, with the result of the validation pipeline. */
data class ScannedGame(
    val displayName: String,
    /** Absolute path, or content:// URI for SAF folders. Passed to the core as-is. */
    val location: String,
    val sizeBytes: Long,
    val info: DiscInfo,
    val serial: String?,
    val match: KnownGames.Match,
    val compat: Int,
    val title: String,
) {
    val playable: Boolean
        get() = serial != null && !info.hasErrors && (info.isComplete || info.format.nativeOnly)
    val widescreenPatch: Boolean get() = serial in KnownGames.widescreenPatchSerials
    val release: KnownGames.Release? get() = KnownGames.find(serial)
}

data class ScanResult(val source: String, val games: List<ScannedGame>, val skipped: List<String>, val error: String? = null)

object GameScanner {

    fun scan(ctx: Context, source: String?): ScanResult {
        val src = source ?: Paths.GAME_DIR
        return try {
            if (src.startsWith("content://")) scanTree(ctx, Uri.parse(src)) else scanDir(File(src))
        } catch (t: Throwable) {
            AppLog.e("Pemindaian gagal", t)
            ScanResult(src, emptyList(), emptyList(), t.message)
        }.also { r ->
            AppLog.i("Scan ${r.source}: ${r.games.size} image, ${r.skipped.size} dilewati" + (r.error?.let { " — $it" } ?: ""))
        }
    }

    private fun scanDir(dir: File): ScanResult {
        if (!dir.exists()) dir.mkdirs()
        if (!dir.isDirectory) return ScanResult(dir.path, emptyList(), emptyList(), "Folder ${dir.path} tidak ada")
        val files = dir.walkTopDown().maxDepth(2)
            .filter { it.isFile && it.extension.lowercase() in Paths.gameExtensions && !it.path.contains("/Save/") }
            .toList()
        if (files.isEmpty() && dir.listFiles() == null) {
            return ScanResult(dir.path, emptyList(), emptyList(), "Tidak ada izin membaca ${dir.path}. Beri akses \"Semua file\" atau pilih folder lewat SAF.")
        }
        val skipped = mutableListOf<String>()
        val games = files.mapNotNull { f ->
            if (f.extension.equals("bin", true) && f.length() in (2L shl 20)..(16L shl 20)) {
                skipped += "${f.name} (ukuran BIOS, bukan game)"; return@mapNotNull null
            }
            val info = runCatching { IsoInspector.inspect(f) }.getOrElse {
                DiscInfo(ImageFormat.UNKNOWN, findings = listOf(Finding(Severity.ERROR, "Gagal dibaca: ${it.message}")))
            }
            val serial = info.serial ?: if (info.format.nativeOnly) nativeSerial { ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY) } else null
            build(f.name, f.absolutePath, f.length(), info, serial)
        }
        return ScanResult(dir.path, games.sortedByDescending { rank(it) }, skipped)
    }

    private fun scanTree(ctx: Context, tree: Uri): ScanResult {
        val root = DocumentFile.fromTreeUri(ctx, tree) ?: return ScanResult(tree.toString(), emptyList(), emptyList(), "Folder SAF tidak valid")
        val docs = mutableListOf<DocumentFile>()
        fun walk(d: DocumentFile, depth: Int) {
            d.listFiles().forEach { c ->
                if (c.isDirectory && depth < 2 && c.name != "Save") walk(c, depth + 1)
                else if (c.isFile && c.name?.substringAfterLast('.', "")?.lowercase() in Paths.gameExtensions) docs += c
            }
        }
        walk(root, 0)
        val skipped = mutableListOf<String>()
        val games = docs.mapNotNull { d ->
            val name = d.name ?: return@mapNotNull null
            if (name.endsWith(".bin", true) && d.length() in (2L shl 20)..(16L shl 20)) {
                skipped += "$name (ukuran BIOS, bukan game)"; return@mapNotNull null
            }
            val info = runCatching {
                ctx.contentResolver.openFileDescriptor(d.uri, "r")!!.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).channel.use { ch -> IsoInspector.inspect(ChannelSource(ch), name) }
                }
            }.getOrElse { DiscInfo(ImageFormat.UNKNOWN, findings = listOf(Finding(Severity.ERROR, "Gagal dibaca: ${it.message}"))) }
            val serial = info.serial ?: if (info.format.nativeOnly) nativeSerial { ctx.contentResolver.openFileDescriptor(d.uri, "r")!! } else null
            build(name, d.uri.toString(), d.length(), info, serial)
        }
        return ScanResult(tree.toString(), games.sortedByDescending { rank(it) }, skipped)
    }

    /** CHD/CSO/ZSO: the core's own readers (libchdr etc.) extract SYSTEM.CNF for us. */
    private fun nativeSerial(open: () -> ParcelFileDescriptor): String? {
        if (NativeApp.hasNoNativeBinary) return null
        return runCatching { open().use { NativeApp.getGameSerialFromFd(it.detachFd()) } }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun build(name: String, location: String, size: Long, info: DiscInfo, serial: String?): ScannedGame {
        val compat = if (serial != null && !NativeApp.hasNoNativeBinary) runCatching { NativeApp.getCompatibilityForSerial(serial) }.getOrDefault(0) else 0
        val title = KnownGames.find(serial)?.title ?: info.volumeId?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.')
        return ScannedGame(name, location, size, info, serial, KnownGames.classify(serial), compat, title)
    }

    private fun rank(g: ScannedGame): Int = when (g.match) {
        KnownGames.Match.TARGET -> 4
        KnownGames.Match.REGIONAL_VARIANT -> 3
        KnownGames.Match.OTHER_GAME -> 2
        KnownGames.Match.UNKNOWN -> 1
    } * 10 + (if (g.playable) 1 else 0)
}
