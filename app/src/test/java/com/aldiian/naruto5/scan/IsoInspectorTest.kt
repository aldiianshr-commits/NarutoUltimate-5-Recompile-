package com.aldiian.naruto5.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsoInspectorTest {

    private val un5Cnf = "BOOT2 = cdrom0:\\SLES_556.05;1\r\nVER = 1.00\r\nVMODE = PAL\r\n"

    @Test fun parsesSystemCnf() {
        val cnf = IsoInspector.parseSystemCnf(un5Cnf)
        assertEquals("cdrom0:\\SLES_556.05;1", cnf.boot2)
        assertEquals("1.00", cnf.version)
        assertEquals("PAL", cnf.videoMode)
    }

    @Test fun serialFromBootPath() {
        assertEquals("SLES-55605", IsoInspector.serialFromBootPath("cdrom0:\\SLES_556.05;1"))
        assertEquals("SLPS-25837", IsoInspector.serialFromBootPath("cdrom0:\\SLPS_258.37;1"))
        assertEquals("SLUS-21234", IsoInspector.serialFromBootPath("cdrom0:\\GAME\\SLUS_212.34"))
        assertNull(IsoInspector.serialFromBootPath("cdrom0:\\MAIN.ELF;1"))
    }

    @Test fun bootPathComponents() {
        assertEquals(listOf("SLES_556.05"), IsoInspector.bootPathComponents("cdrom0:\\SLES_556.05;1"))
        assertEquals(listOf("BIN", "MAIN.ELF"), IsoInspector.bootPathComponents("cdrom0:\\BIN\\MAIN.ELF"))
    }

    @Test fun validIsoIsComplete() {
        val iso = TestIso.build(systemCnf = un5Cnf, elfName = "SLES_556.05;1", elf = TestIso.mipsElf())
        val info = IsoInspector.inspect(ByteArraySource(iso), "Naruto5.iso")
        assertEquals(ImageFormat.ISO, info.format)
        assertEquals("SLES-55605", info.serial)
        assertTrue(info.elfFound)
        assertTrue(info.elfValid)
        assertTrue(info.findings.joinToString { it.message }, info.isComplete)
        assertEquals("NARUTO5", info.volumeId)
    }

    @Test fun rawBinImageIsParsed() {
        val iso = TestIso.build(systemCnf = un5Cnf, elfName = "SLES_556.05;1", elf = TestIso.mipsElf())
        val bin = TestIso.toRawMode2(iso)
        val info = IsoInspector.inspect(ByteArraySource(bin), "Naruto5.bin")
        assertEquals(ImageFormat.BIN_RAW, info.format)
        assertEquals(2352, info.sectorSize)
        assertEquals("SLES-55605", info.serial)
        assertTrue(info.isComplete)
    }

    @Test fun truncatedImageIsReported() {
        val iso = TestIso.build(systemCnf = un5Cnf, elfName = "SLES_556.05;1", elf = TestIso.mipsElf(), padSectors = 40)
        val cut = iso.copyOf(iso.size - 30 * 2048)
        val info = IsoInspector.inspect(ByteArraySource(cut), "cut.iso")
        assertTrue(info.hasErrors)
        assertFalse(info.isComplete)
        assertTrue(info.findings.any { it.severity == Severity.ERROR && it.message.contains("terpotong") })
    }

    @Test fun missingExecutableIsError() {
        val iso = TestIso.build(systemCnf = un5Cnf, elfName = "OTHER.ELF;1", elf = TestIso.mipsElf())
        val info = IsoInspector.inspect(ByteArraySource(iso), "x.iso")
        assertFalse(info.elfFound)
        assertTrue(info.hasErrors)
    }

    @Test fun nonElfExecutableIsError() {
        val iso = TestIso.build(systemCnf = un5Cnf, elfName = "SLES_556.05;1", elf = ByteArray(4096) { 0x41 })
        val info = IsoInspector.inspect(ByteArraySource(iso), "x.iso")
        assertTrue(info.elfFound)
        assertFalse(info.elfValid)
        assertFalse(info.isComplete)
    }

    @Test fun ps1DiscIsRejected() {
        val iso = TestIso.build(systemCnf = "BOOT = cdrom:\\SLUS_000.01;1\r\n", elfName = "SLUS_000.01;1", elf = TestIso.mipsElf())
        val info = IsoInspector.inspect(ByteArraySource(iso), "ps1.iso")
        assertTrue(info.findings.any { it.message.contains("PS1") })
        assertFalse(info.isComplete)
    }

    @Test fun garbageIsNotIso() {
        val info = IsoInspector.inspect(ByteArraySource(ByteArray(64 * 2048) { 7 }), "junk.iso")
        assertEquals(ImageFormat.UNKNOWN, info.format)
        assertTrue(info.hasErrors)
    }

    @Test fun compressedContainersAreDetected() {
        fun head(s: String) = s.toByteArray(Charsets.ISO_8859_1).copyOf(4096)
        assertEquals(ImageFormat.CHD, IsoInspector.inspect(ByteArraySource(head("MComprHD")), "a.chd").format)
        assertEquals(ImageFormat.CSO, IsoInspector.inspect(ByteArraySource(head("CISO")), "a.cso").format)
        assertEquals(ImageFormat.ZSO, IsoInspector.inspect(ByteArraySource(head("ZISO")), "a.zso").format)
    }

    @Test fun knownGamesTable() {
        assertEquals(KnownGames.Match.TARGET, KnownGames.classify("SLES-55605"))
        assertEquals(KnownGames.Match.REGIONAL_VARIANT, KnownGames.classify("SLPS-25837"))
        assertEquals(KnownGames.Match.OTHER_GAME, KnownGames.classify("SLUS-20000"))
        assertEquals(KnownGames.Match.UNKNOWN, KnownGames.classify(null))
    }
}

/** Builds minimal but structurally real ISO 9660 images for tests. */
object TestIso {
    private const val S = 2048

    fun mipsElf(): ByteArray {
        val b = ByteArray(6000)
        b[0] = 0x7F; b[1] = 'E'.code.toByte(); b[2] = 'L'.code.toByte(); b[3] = 'F'.code.toByte()
        b[4] = 1; b[5] = 1; b[6] = 1
        b[16] = 2 // ET_EXEC
        b[18] = 8 // EM_MIPS
        return b
    }

    fun build(systemCnf: String, elfName: String, elf: ByteArray, padSectors: Int = 0): ByteArray {
        val cnf = systemCnf.toByteArray(Charsets.ISO_8859_1)
        val rootLba = 18
        val cnfLba = 19
        val elfLba = 20
        val elfSectors = (elf.size + S - 1) / S
        val total = elfLba + elfSectors + padSectors
        val img = ByteArray(total * S)

        // Primary Volume Descriptor (sector 16)
        val pvd = 16 * S
        img[pvd] = 1
        "CD001".toByteArray().copyInto(img, pvd + 1)
        img[pvd + 6] = 1
        "NARUTO5".padEnd(32).toByteArray().copyInto(img, pvd + 40)
        putBoth32(img, pvd + 80, total.toLong())
        putBoth16(img, pvd + 128, S)
        dirRecord(img, pvd + 156, rootLba, S, true, byteArrayOf(0))
        // Terminator (sector 17)
        img[17 * S] = -1; "CD001".toByteArray().copyInto(img, 17 * S + 1)

        // Root directory
        var p = rootLba * S
        p += dirRecord(img, p, rootLba, S, true, byteArrayOf(0))
        p += dirRecord(img, p, rootLba, S, true, byteArrayOf(1))
        p += dirRecord(img, p, cnfLba, cnf.size, false, "SYSTEM.CNF;1".toByteArray())
        dirRecord(img, p, elfLba, elf.size, false, elfName.toByteArray())

        cnf.copyInto(img, cnfLba * S)
        elf.copyInto(img, elfLba * S)
        return img
    }

    /** Converts a cooked ISO into a raw 2352-byte Mode 2 Form 1 image. */
    fun toRawMode2(iso: ByteArray): ByteArray {
        val n = iso.size / S
        val out = ByteArray(n * 2352)
        for (i in 0 until n) {
            val o = i * 2352
            out[o] = 0
            for (k in 1..10) out[o + k] = -1
            out[o + 11] = 0
            out[o + 15] = 2 // mode 2
            System.arraycopy(iso, i * S, out, o + 24, S)
        }
        return out
    }

    private fun dirRecord(img: ByteArray, at: Int, lba: Int, size: Int, dir: Boolean, name: ByteArray): Int {
        var len = 33 + name.size
        if (len % 2 == 1) len++
        img[at] = len.toByte()
        putBoth32(img, at + 2, lba.toLong())
        putBoth32(img, at + 10, size.toLong())
        img[at + 25] = if (dir) 2 else 0
        putBoth16(img, at + 28, 1)
        img[at + 32] = name.size.toByte()
        name.copyInto(img, at + 33)
        return len
    }

    private fun putBoth32(b: ByteArray, o: Int, v: Long) {
        for (i in 0..3) b[o + i] = (v shr (8 * i)).toByte()
        for (i in 0..3) b[o + 4 + i] = (v shr (8 * (3 - i))).toByte()
    }

    private fun putBoth16(b: ByteArray, o: Int, v: Int) {
        b[o] = v.toByte(); b[o + 1] = (v shr 8).toByte()
        b[o + 2] = (v shr 8).toByte(); b[o + 3] = v.toByte()
    }
}
