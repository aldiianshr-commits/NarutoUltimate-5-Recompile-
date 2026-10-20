package com.aldiian.naruto5.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IniFileTest {
    private val sample = """
        [UI]
        SettingsVersion = 1

        [Folders]
        Bios = /data/old/bios
        Snapshots = snaps
        ; comment = keep

        [EmuCore/GS]
        upscale_multiplier = 2
    """.trimIndent() + "\n"

    @Test fun replacesAndInsertsInsideSection() {
        val out = IniFile.update(sample, "Folders", mapOf("Bios" to "/new/bios", "MemoryCards" to "/sd/Save/memcards"))
        assertTrue(out.contains("Bios = /new/bios"))
        assertTrue(out.contains("MemoryCards = /sd/Save/memcards"))
        assertTrue(out.contains("Snapshots = snaps"))
        assertTrue(out.contains("; comment = keep"))
        // Inserted key stays inside [Folders], before the next section.
        assertTrue(out.indexOf("MemoryCards") < out.indexOf("[EmuCore/GS]"))
        assertTrue(out.indexOf("MemoryCards") > out.indexOf("[Folders]"))
        assertTrue(out.contains("upscale_multiplier = 2"))
    }

    @Test fun appendsMissingSection() {
        val out = IniFile.update("[UI]\nSettingsVersion = 1\n", "Folders", mapOf("Savestates" to "/x"))
        assertEquals("[UI]\nSettingsVersion = 1\n\n[Folders]\nSavestates = /x\n", out)
    }

    @Test fun idempotent() {
        val once = IniFile.update(sample, "Folders", mapOf("MemoryCards" to "/m"))
        assertEquals(once, IniFile.update(once, "Folders", mapOf("MemoryCards" to "/m")))
    }
}
