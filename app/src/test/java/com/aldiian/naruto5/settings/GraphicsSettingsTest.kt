package com.aldiian.naruto5.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphicsSettingsTest {

    @Test fun roundTripSerialization() {
        val s = GraphicsSettings(
            preset = Preset.CUSTOM, renderer = Renderer.OPENGL, resolution = Resolution.P1080,
            fpsLimit = FpsLimit.FPS45, textureQuality = TextureQuality.HIGH, antiAliasing = AntiAliasing.FXAA,
            anisotropy = Anisotropy.X16, vsync = true, shaderCache = false, gpuDriver = GpuDriver.TURNIP,
            turnipDriverId = "armsx2turnip-v1", aspect = AspectMode.R16_10, fullscreen = false,
            perfProfile = PerfProfile.PERFORMANCE, showFps = true,
        )
        assertEquals(s, GraphicsSettings.deserialize(s.serialize()))
    }

    @Test fun garbageFallsBackToDefaults() {
        assertEquals(GraphicsSettings.DEFAULT, GraphicsSettings.deserialize("renderer=NOPE\nvsync=maybe"))
        assertEquals(GraphicsSettings.DEFAULT, GraphicsSettings.deserialize(null))
    }

    @Test fun presetsAreOrderedByCost() {
        val base = GraphicsSettings.DEFAULT
        val order = listOf(Preset.VERY_LOW, Preset.LOW, Preset.MEDIUM, Preset.HIGH, Preset.ULTRA).map { base.withPreset(it) }
        val mult = order.map { it.resolution.multiplier }
        assertEquals(mult.sorted(), mult)
        assertEquals(Resolution.P1080, base.withPreset(Preset.ULTRA).resolution)
        assertEquals(FpsLimit.FPS30, base.withPreset(Preset.VERY_LOW).fpsLimit)
        // Presets never change user-owned choices like renderer or driver.
        val custom = base.copy(renderer = Renderer.OPENGL, gpuDriver = GpuDriver.TURNIP)
        assertEquals(Renderer.OPENGL, custom.withPreset(Preset.ULTRA).renderer)
        assertEquals(GpuDriver.TURNIP, custom.withPreset(Preset.LOW).gpuDriver)
    }

    @Test fun mappingWritesRealCoreKeys() {
        val s = GraphicsSettings.DEFAULT.copy(resolution = Resolution.P900, antiAliasing = AntiAliasing.FXAA,
            anisotropy = Anisotropy.X8, shaderCache = false, vsync = true, textureQuality = TextureQuality.LOW)
        val m = CoreMapping.coreSettings(s, widescreenAvailable = true).associateBy { it.key }
        assertEquals("2.0", m.getValue("upscale_multiplier").value)
        assertEquals("true", m.getValue("fxaa").value)
        assertEquals("8", m.getValue("MaxAnisotropy").value)
        assertEquals("true", m.getValue("DisableShaderCache").value)
        assertEquals("true", m.getValue("VsyncEnable").value)
        assertEquals("0", m.getValue("filter").value)
        assertEquals("false", m.getValue("hw_mipmap").value)
        assertTrue(m.values.all { it.type in setOf("bool", "int", "float", "string") })
    }

    @Test fun aspectNeedsWidescreenPatch() {
        val wide = GraphicsSettings.DEFAULT.copy(aspect = AspectMode.R16_10)
        val withPatch = CoreMapping.coreSettings(wide, true).associateBy { it.key }
        assertEquals("Custom", withPatch.getValue("AspectRatio").value)
        assertEquals("1.6", withPatch.getValue("CustomAspectRatio").value)
        assertTrue(CoreMapping.widescreenPatchEnabled(wide, true))

        val noPatch = CoreMapping.coreSettings(wide, false).associateBy { it.key }
        assertEquals("Auto 4:3/3:2", noPatch.getValue("AspectRatio").value)
        assertFalse(CoreMapping.widescreenPatchEnabled(wide, false))
    }

    @Test fun fpsCapAndProfiles() {
        assertEquals(0, CoreMapping.fpsCap(GraphicsSettings.DEFAULT.copy(fpsLimit = FpsLimit.FPS60)))
        assertEquals(45, CoreMapping.fpsCap(GraphicsSettings.DEFAULT.copy(fpsLimit = FpsLimit.FPS45)))
        assertEquals(30, CoreMapping.fpsCap(GraphicsSettings.DEFAULT.copy(fpsLimit = FpsLimit.FPS30)))
        assertEquals(7, CoreMapping.affinityMode(GraphicsSettings.DEFAULT.copy(perfProfile = PerfProfile.PERFORMANCE)))
        assertEquals(0, CoreMapping.affinityMode(GraphicsSettings.DEFAULT.copy(perfProfile = PerfProfile.BATTERY)))
        assertFalse(CoreMapping.adpfEnabled(GraphicsSettings.DEFAULT.copy(perfProfile = PerfProfile.BATTERY)))
    }
}
