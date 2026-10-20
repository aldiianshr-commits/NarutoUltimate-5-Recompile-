package com.aldiian.naruto5.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File

/**
 * Folder layout.
 *
 *  /storage/emulated/0/Naruto5/            game images (+ optional BIOS/ sub-folder)
 *  /storage/emulated/0/Naruto5/Save/       memcards/, states/, backups/
 *  <app external files>/                   core data root: PCSX2-Android.ini, shader cache, resources
 *  <app internal files>/bios/              BIOS (app-private: native code must be able to open it)
 *  <app internal files>/drivers/           Turnip driver packs (must be dlopen-able, so internal)
 */
object Paths {
    const val GAME_DIR = "/storage/emulated/0/Naruto5"
    const val SAVE_DIR = "/storage/emulated/0/Naruto5/Save"

    val gameExtensions = setOf("iso", "bin", "chd", "cso", "zso", "gz", "elf")

    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

    fun gameDir(): File = File(GAME_DIR)

    /** Save root: the requested public folder when we can write there, else an app-owned fallback. */
    fun saveRoot(ctx: Context): File {
        val pub = File(SAVE_DIR)
        if (hasAllFilesAccess()) {
            pub.mkdirs()
            if (pub.isDirectory && pub.canWrite()) return pub
        }
        return File(dataRoot(ctx), "Save").apply { mkdirs() }
    }

    fun usingPublicSaveDir(ctx: Context): Boolean = saveRoot(ctx).absolutePath == SAVE_DIR

    fun memcardDir(ctx: Context) = File(saveRoot(ctx), "memcards").apply { mkdirs() }
    fun statesDir(ctx: Context) = File(saveRoot(ctx), "states").apply { mkdirs() }
    fun backupDir(ctx: Context) = File(saveRoot(ctx), "backups").apply { mkdirs() }

    fun dataRoot(ctx: Context): File = (ctx.getExternalFilesDir(null) ?: ctx.filesDir).apply { mkdirs() }
    fun biosDir(ctx: Context): File = File(ctx.filesDir, "bios").apply { mkdirs() }
    fun driversDir(ctx: Context): File = File(ctx.filesDir, "drivers").apply { mkdirs() }
    fun logsDir(ctx: Context): File = File(dataRoot(ctx), "logs").apply { mkdirs() }
}
