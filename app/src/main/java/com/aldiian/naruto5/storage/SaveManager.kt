package com.aldiian.naruto5.storage

import android.content.Context
import com.aldiian.naruto5.diag.AppLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Memory cards, save states and zip backups under the save folder. */
object SaveManager {
    data class SaveFile(val file: File, val kind: String) {
        val name: String get() = file.name
        val size: Long get() = file.length()
        val modified: Long get() = file.lastModified()
    }

    fun memoryCards(ctx: Context): List<SaveFile> =
        Paths.memcardDir(ctx).listFiles()?.filter { it.isFile || it.isDirectory }?.map { SaveFile(it, "Memory card") }
            ?.sortedBy { it.name } ?: emptyList()

    fun saveStates(ctx: Context): List<SaveFile> =
        Paths.statesDir(ctx).listFiles()?.filter { it.isFile && (it.extension == "p2s" || it.name.endsWith(".p2s.backup")) }
            ?.map { SaveFile(it, "Save state") }?.sortedByDescending { it.modified } ?: emptyList()

    fun backups(ctx: Context): List<File> =
        Paths.backupDir(ctx).listFiles()?.filter { it.isFile && it.extension == "zip" }?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** Zips memcards/ and states/ into backups/naruto5-save-<timestamp>.zip. */
    fun backup(ctx: Context): File {
        val root = Paths.saveRoot(ctx)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val out = File(Paths.backupDir(ctx), "naruto5-save-$stamp.zip")
        var files = 0
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            listOf(Paths.memcardDir(ctx), Paths.statesDir(ctx)).forEach { dir ->
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    zip.putNextEntry(ZipEntry(f.relativeTo(root).path))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    files++
                }
            }
        }
        AppLog.i("Backup dibuat: ${out.name} ($files file)")
        return out
    }

    /**
     * Restores a backup. The current saves are first backed up automatically, so a restore can
     * always be undone. Only memcards/ and states/ entries are written (no path traversal).
     */
    fun restore(ctx: Context, zipFile: File): Int {
        val safety = backup(ctx)
        AppLog.i("Backup pengaman sebelum restore: ${safety.name}")
        val root = Paths.saveRoot(ctx).canonicalFile
        var n = 0
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                val dest = File(root, e.name).canonicalFile
                val okPrefix = listOf("memcards", "states").any { dest.path.startsWith(File(root, it).path + File.separator) }
                if (!okPrefix) continue
                dest.parentFile?.mkdirs()
                dest.outputStream().use { zip.copyTo(it) }
                n++
            }
        }
        AppLog.i("Restore ${zipFile.name}: $n file dipulihkan")
        return n
    }

    fun delete(f: File) = if (f.isDirectory) f.deleteRecursively() else f.delete()
}
