package com.aldiian.naruto5.settings

/**
 * Graphics / performance configuration and its exact mapping onto ARMSX2 (PCSX2) core settings.
 *
 * Every option here changes a real core setting (see [CoreMapping]). Options the emulator has no
 * equivalent for are deliberately absent:
 *  - Shadow quality: PS2 games render their own shadows; PCSX2 has no knob for it.
 *  - MSAA: the PCSX2 hardware renderers do not implement MSAA (upscaling replaces it).
 * The UI shows those two rows disabled with that explanation instead of offering fake toggles.
 */
enum class Renderer(val label: String) {
    AUTO("Otomatis"), VULKAN("Vulkan"), OPENGL("OpenGL ES"), SOFTWARE("Software (CPU)")
}

enum class Resolution(val label: String, val multiplier: Float) {
    P480("480p (1× native)", 1.0f),
    P720("720p (1.5×)", 1.5f),
    P900("900p (2×)", 2.0f),
    P1080("1080p (2.5×)", 2.5f),
}

enum class FpsLimit(val fps: Int, val label: String) {
    FPS30(30, "30 FPS"), FPS45(45, "45 FPS"), FPS60(60, "60 FPS (penuh)")
}

/** Maps to PCSX2 texture filtering ("filter": BiFiltering) + hardware mipmapping. */
enum class TextureQuality(val label: String, val filter: Int, val hwMipmap: Boolean) {
    LOW("Rendah (nearest)", 0, false),
    MEDIUM("Sedang (bilinear PS2)", 2, true),
    HIGH("Tinggi (bilinear paksa, kecuali sprite)", 3, true),
}

enum class AntiAliasing(val label: String) { OFF("Mati"), FXAA("FXAA") }

enum class Anisotropy(val value: Int, val label: String) {
    OFF(0, "Mati"), X2(2, "2×"), X4(4, "4×"), X8(8, "8×"), X16(16, "16×")
}

enum class AspectMode(val label: String) {
    AUTO("Otomatis (4:3 asli)"), R16_9("16:9 (patch widescreen)"), R16_10("16:10 (layar Pad 6)")
}

enum class GpuDriver(val label: String) { SYSTEM("System Driver"), TURNIP("Turnip (Mesa)") }

enum class PerfProfile(val label: String, val description: String) {
    BALANCED("Seimbang", "Penjadwalan CPU bawaan, MTVU aktif, ADPF aktif."),
    PERFORMANCE("Performa", "Thread emulator dikunci ke core besar (Cortex-A77), MTVU aktif."),
    BATTERY("Hemat Daya", "Penjadwalan bawaan, mode sustained performance untuk suhu stabil."),
}

enum class Preset(val label: String) {
    VERY_LOW("Very Low"), LOW("Low"), MEDIUM("Medium"), HIGH("High"), ULTRA("Ultra"), CUSTOM("Custom")
}

data class GraphicsSettings(
    val preset: Preset = Preset.MEDIUM,
    val renderer: Renderer = Renderer.AUTO,
    val resolution: Resolution = Resolution.P720,
    val fpsLimit: FpsLimit = FpsLimit.FPS60,
    val textureQuality: TextureQuality = TextureQuality.MEDIUM,
    val antiAliasing: AntiAliasing = AntiAliasing.OFF,
    val anisotropy: Anisotropy = Anisotropy.X4,
    val vsync: Boolean = false,
    val shaderCache: Boolean = true,
    val gpuDriver: GpuDriver = GpuDriver.SYSTEM,
    val turnipDriverId: String = "",
    val aspect: AspectMode = AspectMode.R16_9,
    val fullscreen: Boolean = true,
    val perfProfile: PerfProfile = PerfProfile.BALANCED,
    val showFps: Boolean = false,
) {
    /** Applies a preset's values, keeping user-level choices that presets do not own. */
    fun withPreset(p: Preset): GraphicsSettings = when (p) {
        Preset.VERY_LOW -> copy(preset = p, resolution = Resolution.P480, fpsLimit = FpsLimit.FPS30,
            textureQuality = TextureQuality.LOW, antiAliasing = AntiAliasing.OFF, anisotropy = Anisotropy.OFF,
            perfProfile = PerfProfile.BATTERY)
        Preset.LOW -> copy(preset = p, resolution = Resolution.P480, fpsLimit = FpsLimit.FPS60,
            textureQuality = TextureQuality.LOW, antiAliasing = AntiAliasing.OFF, anisotropy = Anisotropy.X2,
            perfProfile = PerfProfile.BALANCED)
        Preset.MEDIUM -> copy(preset = p, resolution = Resolution.P720, fpsLimit = FpsLimit.FPS60,
            textureQuality = TextureQuality.MEDIUM, antiAliasing = AntiAliasing.OFF, anisotropy = Anisotropy.X4,
            perfProfile = PerfProfile.BALANCED)
        Preset.HIGH -> copy(preset = p, resolution = Resolution.P900, fpsLimit = FpsLimit.FPS60,
            textureQuality = TextureQuality.HIGH, antiAliasing = AntiAliasing.FXAA, anisotropy = Anisotropy.X8,
            perfProfile = PerfProfile.PERFORMANCE)
        Preset.ULTRA -> copy(preset = p, resolution = Resolution.P1080, fpsLimit = FpsLimit.FPS60,
            textureQuality = TextureQuality.HIGH, antiAliasing = AntiAliasing.FXAA, anisotropy = Anisotropy.X16,
            perfProfile = PerfProfile.PERFORMANCE)
        Preset.CUSTOM -> copy(preset = p)
    }

    // ---- persistence: simple "key=value" lines (stable, no JSON dependency) -------------------
    fun serialize(): String = listOf(
        "preset" to preset.name, "renderer" to renderer.name, "resolution" to resolution.name,
        "fpsLimit" to fpsLimit.name, "textureQuality" to textureQuality.name,
        "antiAliasing" to antiAliasing.name, "anisotropy" to anisotropy.name,
        "vsync" to vsync.toString(), "shaderCache" to shaderCache.toString(),
        "gpuDriver" to gpuDriver.name, "turnipDriverId" to turnipDriverId, "aspect" to aspect.name,
        "fullscreen" to fullscreen.toString(), "perfProfile" to perfProfile.name, "showFps" to showFps.toString(),
    ).joinToString("\n") { "${it.first}=${it.second}" }

    companion object {
        /** Factory defaults tuned for Xiaomi Pad 6 (Snapdragon 870 / Adreno 650). */
        val DEFAULT = GraphicsSettings()

        fun deserialize(text: String?): GraphicsSettings {
            if (text.isNullOrBlank()) return DEFAULT
            val m = text.lines().mapNotNull { l ->
                val i = l.indexOf('='); if (i <= 0) null else l.substring(0, i) to l.substring(i + 1)
            }.toMap()
            val d = DEFAULT
            return GraphicsSettings(
                preset = enumOr(m["preset"], d.preset),
                renderer = enumOr(m["renderer"], d.renderer),
                resolution = enumOr(m["resolution"], d.resolution),
                fpsLimit = enumOr(m["fpsLimit"], d.fpsLimit),
                textureQuality = enumOr(m["textureQuality"], d.textureQuality),
                antiAliasing = enumOr(m["antiAliasing"], d.antiAliasing),
                anisotropy = enumOr(m["anisotropy"], d.anisotropy),
                vsync = m["vsync"]?.toBooleanStrictOrNull() ?: d.vsync,
                shaderCache = m["shaderCache"]?.toBooleanStrictOrNull() ?: d.shaderCache,
                gpuDriver = enumOr(m["gpuDriver"], d.gpuDriver),
                turnipDriverId = m["turnipDriverId"] ?: d.turnipDriverId,
                aspect = enumOr(m["aspect"], d.aspect),
                fullscreen = m["fullscreen"]?.toBooleanStrictOrNull() ?: d.fullscreen,
                perfProfile = enumOr(m["perfProfile"], d.perfProfile),
                showFps = m["showFps"]?.toBooleanStrictOrNull() ?: d.showFps,
            )
        }

        private inline fun <reified E : Enum<E>> enumOr(v: String?, def: E): E =
            v?.let { s -> enumValues<E>().firstOrNull { it.name == s } } ?: def
    }
}

/** One write into the core's INI-backed settings layer (NativeApp.setSetting). */
data class CoreSetting(val section: String, val key: String, val type: String, val value: String)

object CoreMapping {
    const val GS = "EmuCore/GS"
    const val WIDESCREEN_PATCH = "Widescreen 16:9"

    /** Settings written before boot (and re-applied live when changed in game). */
    fun coreSettings(s: GraphicsSettings, widescreenAvailable: Boolean): List<CoreSetting> = buildList {
        add(CoreSetting(GS, "upscale_multiplier", "float", s.resolution.multiplier.toString()))
        add(CoreSetting(GS, "filter", "int", s.textureQuality.filter.toString()))
        add(CoreSetting(GS, "hw_mipmap", "bool", s.textureQuality.hwMipmap.toString()))
        add(CoreSetting(GS, "fxaa", "bool", (s.antiAliasing == AntiAliasing.FXAA).toString()))
        add(CoreSetting(GS, "MaxAnisotropy", "int", s.anisotropy.value.toString()))
        add(CoreSetting(GS, "VsyncEnable", "bool", s.vsync.toString()))
        add(CoreSetting(GS, "DisableShaderCache", "bool", (!s.shaderCache).toString()))
        when (aspectFor(s, widescreenAvailable)) {
            AspectMode.AUTO -> add(CoreSetting(GS, "AspectRatio", "string", "Auto 4:3/3:2"))
            AspectMode.R16_9 -> add(CoreSetting(GS, "AspectRatio", "string", "16:9"))
            AspectMode.R16_10 -> {
                add(CoreSetting(GS, "AspectRatio", "string", "Custom"))
                add(CoreSetting(GS, "CustomAspectRatio", "float", "1.6"))
            }
        }
        // FMVs are 4:3 masters: show them unstretched even when gameplay is widescreen.
        add(CoreSetting(GS, "FMVAspectRatioSwitch", "string", "Auto 4:3/3:2"))
        // Multi-threaded VU1 (MTVU): big win on 8-core Snapdragon parts, safe for this title.
        add(CoreSetting("EmuCore/Speedhacks", "vuThread", "bool", "true"))
        add(CoreSetting("EmuCore", "EnableFastBoot", "bool", "true"))
        // This app feeds the pad directly (NativeApp.setPadButton); SDL's own pad source would
        // double every press with its default bindings.
        add(CoreSetting("InputSources", "SDL", "bool", "false"))
    }

    /** Widescreen needs the game's 16:9 patch; without it 16:9/16:10 would only stretch 4:3. */
    fun aspectFor(s: GraphicsSettings, widescreenAvailable: Boolean): AspectMode =
        if (widescreenAvailable) s.aspect else AspectMode.AUTO

    fun widescreenPatchEnabled(s: GraphicsSettings, widescreenAvailable: Boolean): Boolean =
        aspectFor(s, widescreenAvailable) != AspectMode.AUTO

    /** Present-rate cap passed to NativeApp.setFpsCap (0 = off; ≥ native rate is a no-op). */
    fun fpsCap(s: GraphicsSettings): Int = if (s.fpsLimit == FpsLimit.FPS60) 0 else s.fpsLimit.fps

    /** NativeApp.setAffinityMode: 0 = scheduler default, 7 = big ("performance") cores only. */
    fun affinityMode(s: GraphicsSettings): Int = if (s.perfProfile == PerfProfile.PERFORMANCE) 7 else 0

    fun adpfEnabled(s: GraphicsSettings): Boolean = s.perfProfile != PerfProfile.BATTERY
}
