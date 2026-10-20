package com.aldiian.naruto5.gpu

import android.content.Context
import android.net.Uri
import com.aldiian.naruto5.diag.AppLog
import com.aldiian.naruto5.storage.Paths
import kr.co.iefriends.pcsx2.NativeApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Turnip (Mesa Vulkan for Adreno) driver packs in the adrenotools format: a .zip containing
 * meta.json + libvulkan_freedreno.so. The core is built with libadrenotools, so a pack selected
 * here is loaded in place of the system Vulkan driver when the GS opens (Vulkan renderer only).
 *
 * Packs are stored in internal storage: the external app dir is mounted noexec and dlopen fails.
 */
object TurnipManager {
    /** ARMSX2's own Turnip builds (Mesa + ARMSX2 patches, built for Adreno 6xx/7xx). */
    const val RELEASES_API = "https://api.github.com/repos/bmdhacks/armsx2-turnip/releases"
    private const val DEFAULT_LIB = "libvulkan_freedreno.so"

    data class Driver(val id: String, val name: String, val version: String, val libraryName: String, val dir: File) {
        val libFile get() = File(dir, libraryName)
    }

    fun installed(ctx: Context): List<Driver> =
        Paths.driversDir(ctx).listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            val meta = File(dir, "meta.json").takeIf { it.isFile }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
                ?: return@mapNotNull null
            val lib = meta.optString("libraryName", DEFAULT_LIB).ifBlank { DEFAULT_LIB }
            Driver(dir.name, meta.optString("name", dir.name), meta.optString("driverVersion", ""), lib, dir)
                .takeIf { it.libFile.isFile }
        }?.sortedBy { it.name } ?: emptyList()

    fun find(ctx: Context, id: String?): Driver? = installed(ctx).firstOrNull { it.id == id }

    /** Points the core at [driver] (or back at the system loader when null). */
    fun applyToCore(ctx: Context, driver: Driver?) {
        if (driver == null) {
            NativeApp.setCustomVulkanDriver("", "", "", "")
            return
        }
        val redirect = File(driver.dir, "cache").apply { mkdirs() }
        NativeApp.setCustomVulkanDriver(
            driver.dir.absolutePath + "/", driver.libraryName, redirect.absolutePath + "/",
            ctx.applicationInfo.nativeLibraryDir,
        )
        AppLog.i("Driver Vulkan kustom: ${driver.name} (${driver.libraryName})")
    }

    fun importZip(ctx: Context, uri: Uri, fallbackName: String = "turnip"): Driver =
        ctx.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "File tidak bisa dibuka" }
            installFromZip(ctx, input, fallbackName)
        }

    /** Downloads the newest pack from the ARMSX2 Turnip releases on GitHub. */
    fun downloadLatest(ctx: Context): Driver {
        val releases = JSONArray(httpGet(RELEASES_API).bufferedReader().use { it.readText() })
        for (i in 0 until releases.length()) {
            val rel = releases.getJSONObject(i)
            if (rel.optBoolean("draft")) continue
            val assets = rel.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                if (a.optString("name").endsWith(".zip", true)) {
                    val name = rel.optString("name").ifBlank { rel.optString("tag_name", "turnip") }
                    AppLog.i("Mengunduh Turnip: $name (${a.optString("name")})")
                    return httpGet(a.getString("browser_download_url")).use { installFromZip(ctx, it, name) }
                }
            }
        }
        error("Tidak ada paket driver .zip di rilis ARMSX2 Turnip")
    }

    fun delete(ctx: Context, d: Driver) { d.dir.deleteRecursively() }

    private fun installFromZip(ctx: Context, input: InputStream, fallbackName: String): Driver {
        val id = "turnip-" + fallbackName.lowercase().replace(Regex("[^a-z0-9._-]+"), "-").take(48)
        val dir = File(Paths.driversDir(ctx), id)
        dir.deleteRecursively(); dir.mkdirs()
        var soName: String? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                val name = File(e.name).name // flatten; reject path traversal
                if (name.isBlank()) continue
                File(dir, name).outputStream().use { zip.copyTo(it) }
                if (name.endsWith(".so") && (soName == null || name.contains("vulkan"))) soName = name
            }
        }
        val metaFile = File(dir, "meta.json")
        val meta = if (metaFile.isFile) runCatching { JSONObject(metaFile.readText()) }.getOrElse { JSONObject() } else JSONObject()
        if (!meta.has("libraryName")) meta.put("libraryName", soName ?: DEFAULT_LIB)
        if (!meta.has("name")) meta.put("name", fallbackName)
        metaFile.writeText(meta.toString(2))
        val d = Driver(id, meta.optString("name"), meta.optString("driverVersion"), meta.getString("libraryName"), dir)
        require(d.libFile.isFile) { "Paket tidak berisi ${d.libraryName}" }
        AppLog.i("Driver terpasang: ${d.name} -> ${d.dir}")
        return d
    }

    private fun httpGet(url: String): InputStream {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 60000
        c.setRequestProperty("User-Agent", "NarutoUltimate5-Android")
        c.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
        c.instanceFollowRedirects = true
        if (c.responseCode !in 200..299) error("HTTP ${c.responseCode} untuk $url")
        return c.inputStream
    }
}
