package com.aldiian.naruto5.storage

import android.content.Context
import com.aldiian.naruto5.input.GamepadMapping
import com.aldiian.naruto5.input.TouchLayout
import com.aldiian.naruto5.settings.GraphicsSettings
import java.io.File

/**
 * Persisted launcher state, stored as one small text file per key under <files>/prefs.
 * Plain files (not SharedPreferences) because the launcher and the emulator run in different
 * processes and SharedPreferences caches values per process.
 */
class AppPrefs(ctx: Context) {
    private val dir = File(ctx.applicationContext.filesDir, "prefs").apply { mkdirs() }

    private fun file(key: String) = File(dir, key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".txt")
    private fun get(key: String): String? = file(key).takeIf { it.isFile }?.readText()
    private fun put(key: String, v: String?) {
        val f = file(key)
        if (v == null) { f.delete(); return }
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(v)
        if (!tmp.renameTo(f)) { f.writeText(v); tmp.delete() }
    }

    /** Absolute folder path, or a content:// tree URI picked through SAF. Null = default folder. */
    var gameSource: String?
        get() = get("gameSource")
        set(v) = put("gameSource", v)

    /** Path or content:// URI of the image last chosen to play. */
    var selectedGame: String?
        get() = get("selectedGame")
        set(v) = put("selectedGame", v)

    var biosFile: String?
        get() = get("biosFile")
        set(v) = put("biosFile", v)

    var assetsStamp: String?
        get() = get("assetsStamp")
        set(v) = put("assetsStamp", v)

    var gpuStrings: String?
        get() = get("gpuStrings")
        set(v) = put("gpuStrings", v)

    fun globalSettings(): GraphicsSettings = GraphicsSettings.deserialize(get("gfx.global"))
    fun saveGlobalSettings(s: GraphicsSettings) = put("gfx.global", s.serialize())

    fun hasGameSettings(serial: String?): Boolean = serial != null && file("gfx.game.$serial").isFile
    fun gameSettings(serial: String?): GraphicsSettings? = serial?.let { s -> get("gfx.game.$s")?.let { GraphicsSettings.deserialize(it) } }
    fun saveGameSettings(serial: String, s: GraphicsSettings) = put("gfx.game.$serial", s.serialize())
    fun clearGameSettings(serial: String) = put("gfx.game.$serial", null)

    /** Per-game settings when present, otherwise the global profile. */
    fun effectiveSettings(serial: String?): GraphicsSettings = gameSettings(serial) ?: globalSettings()

    fun touchLayout(): TouchLayout = TouchLayout.deserialize(get("touchLayout"))
    fun saveTouchLayout(l: TouchLayout) = put("touchLayout", l.serialize())

    fun gamepadMapping(): GamepadMapping = GamepadMapping.deserialize(get("gamepad"))
    fun saveGamepadMapping(m: GamepadMapping) = put("gamepad", m.serialize())
}
