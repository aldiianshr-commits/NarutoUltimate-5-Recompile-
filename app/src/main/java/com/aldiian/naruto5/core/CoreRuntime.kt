package com.aldiian.naruto5.core

import android.content.Context
import android.os.Build
import com.aldiian.naruto5.BuildConfig
import com.aldiian.naruto5.bios.BiosManager
import com.aldiian.naruto5.diag.AppLog
import com.aldiian.naruto5.gpu.GpuInfo
import com.aldiian.naruto5.gpu.TurnipManager
import com.aldiian.naruto5.settings.CoreMapping
import com.aldiian.naruto5.settings.GpuDriver
import com.aldiian.naruto5.settings.GraphicsSettings
import com.aldiian.naruto5.settings.Renderer
import com.aldiian.naruto5.storage.AppPrefs
import com.aldiian.naruto5.storage.Paths
import kr.co.iefriends.pcsx2.NativeApp
import java.io.File

/**
 * Owns the ARMSX2 core's process-wide state: runtime resources, data root, folders, settings.
 * Everything here is real core configuration written through NativeApp.setSetting into the core's
 * INI layer (<data root>/PCSX2-Android.ini) and read by VMManager at boot.
 */
object CoreRuntime {
    @Volatile var initialized = false
        private set

    val available: Boolean get() = !NativeApp.hasNoNativeBinary

    /** Idempotent; safe to call from a background thread. */
    @Synchronized
    fun ensureInitialized(ctx: Context, prefs: AppPrefs): Boolean {
        if (initialized) return true
        if (!available) {
            AppLog.e("Core tidak tersedia: ${NativeApp.loadError}")
            return false
        }
        val dataRoot = Paths.dataRoot(ctx)
        syncResources(ctx, prefs, dataRoot)

        val gpu = GpuInfo.get(prefs)
        if (gpu.renderer.isNotBlank()) NativeApp.setAutoRendererGpuStrings(gpu.vendor, gpu.renderer, gpu.version)

        // Folders: memory cards and save states go to the user-visible save folder. The core
        // reads [Folders] only inside initialize (VMManager::Internal::LoadStartupSettings), so
        // when the INI already exists the paths are written into it *before* initialize.
        val folders = linkedMapOf(
            "Bios" to Paths.biosDir(ctx).absolutePath,
            "MemoryCards" to Paths.memcardDir(ctx).absolutePath,
            "Savestates" to Paths.statesDir(ctx).absolutePath,
        )
        val ini = File(dataRoot, "PCSX2-Android.ini")
        val iniExisted = ini.isFile && ini.length() > 0
        if (iniExisted) runCatching { ini.writeText(IniFile.update(ini.readText(), "Folders", folders)) }
            .onFailure { AppLog.w("INI tidak bisa diperbarui: ${it.message}") }

        val biosDir = Paths.biosDir(ctx).absolutePath
        NativeApp.initialize(dataRoot.absolutePath, biosDir, Build.VERSION.SDK_INT)
        // Same JNI bring-up the ARMSX2 frontend performs after initialize: SDL's Java side must be
        // wired even though this app feeds the pad itself (SDL is linked into the core).
        runCatching {
            org.libsdl.app.SDLControllerManager.nativeSetupJNI()
            org.libsdl.app.SDLControllerManager.initialize()
        }.onFailure { AppLog.w("SDL JNI setup: ${it.message}") }
        folders.forEach { (k, v) -> set("Folders", k, "string", v) }
        set("MemoryCards", "Slot1_Enable", "bool", "true")
        NativeApp.commitSettings()
        if (!iniExisted) {
            // Very first start: the core has just seeded its defaults; run its startup settings
            // load once more so the folders above are in effect for this process too.
            NativeApp.initialize(dataRoot.absolutePath, biosDir, Build.VERSION.SDK_INT)
        }
        initialized = true
        AppLog.i("Folder core: memcards=${folders["MemoryCards"]} states=${folders["Savestates"]}")
        AppLog.i("Core diinisialisasi: ${NativeApp.getBuildVersion()} data=${dataRoot.path}")
        return true
    }

    /** Pushes graphics/performance settings for the game about to run (or running). */
    fun applySettings(ctx: Context, s: GraphicsSettings, serial: String?, widescreenAvailable: Boolean) {
        if (!initialized) return
        BiosManager.current(ctx, AppPrefs(ctx))?.let { set("Filenames", "BIOS", "string", it.file.name) }

        CoreMapping.coreSettings(s, widescreenAvailable).forEach { set(it.section, it.key, it.type, it.value) }

        // GPU driver must be pinned before the renderer is selected (the VK loader reads it lazily).
        val turnip = if (s.gpuDriver == GpuDriver.TURNIP) TurnipManager.find(ctx, s.turnipDriverId)
            ?: TurnipManager.installed(ctx).firstOrNull() else null
        TurnipManager.applyToCore(ctx, turnip)
        when (s.renderer) {
            Renderer.AUTO -> NativeApp.renderAuto()
            Renderer.VULKAN -> NativeApp.renderVulkan()
            Renderer.OPENGL -> NativeApp.renderOpenGL()
            Renderer.SOFTWARE -> NativeApp.renderSoftware()
        }
        NativeApp.renderUpscalemultiplier(s.resolution.multiplier)

        // Widescreen 16:9 pnach from the core's patches.zip (only shipped for SLES-55605).
        val patchNames = arrayOf(CoreMapping.WIDESCREEN_PATCH)
        val enabled = if (CoreMapping.widescreenPatchEnabled(s, widescreenAvailable)) patchNames else emptyArray()
        NativeApp.setEnabledPatches(false, patchNames, enabled, serial ?: "")

        NativeApp.setAffinityMode(CoreMapping.affinityMode(s))
        NativeApp.setAdpfEnabled(CoreMapping.adpfEnabled(s))
        NativeApp.osdShowFPS(s.showFps)
        NativeApp.commitSettings()
        AppLog.i("Pengaturan diterapkan: ${s.preset.label}, ${s.renderer.label}, ${s.resolution.label}, " +
            "${s.fpsLimit.label}, AA=${s.antiAliasing.label}, AF=${s.anisotropy.label}, aspek=${CoreMapping.aspectFor(s, widescreenAvailable).label}, " +
            "driver=${turnip?.name ?: "system"}, profil=${s.perfProfile.label}")
    }

    /** Re-applies settings to a running VM (GS options live; renderer/driver need a restart). */
    fun applyLive(ctx: Context, s: GraphicsSettings, serial: String?, widescreenAvailable: Boolean) {
        applySettings(ctx, s, serial, widescreenAvailable)
        runCatching { NativeApp.applyGSSettingsLive() }
        runCatching { NativeApp.reloadPatches() }
        NativeApp.setFpsCap(CoreMapping.fpsCap(s))
    }

    private fun set(section: String, key: String, type: String, value: String) =
        NativeApp.setSetting(section, key, type, value)

    /** Copies GameDB / shaders / fonts / patches.zip from the APK into <data root>/resources. */
    private fun syncResources(ctx: Context, prefs: AppPrefs, dataRoot: File) {
        val install = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime }.getOrDefault(0L)
        val stamp = "${BuildConfig.VERSION_CODE}-${BuildConfig.CORE_COMMIT}-$install"
        val target = File(dataRoot, "resources")
        if (prefs.assetsStamp == stamp && File(target, "GameIndex.yaml").isFile) return
        val t0 = System.currentTimeMillis()
        var count = 0
        fun copy(assetPath: String, dest: File) {
            val children = ctx.assets.list(assetPath) ?: emptyArray()
            if (children.isEmpty()) {
                dest.parentFile?.mkdirs()
                ctx.assets.open(assetPath).use { input -> dest.outputStream().use { input.copyTo(it) } }
                count++
            } else {
                dest.mkdirs()
                children.forEach { copy("$assetPath/$it", File(dest, it)) }
            }
        }
        copy("resources", target)
        prefs.assetsStamp = stamp
        AppLog.i("Resource core disalin: $count file dalam ${System.currentTimeMillis() - t0} ms")
    }
}
