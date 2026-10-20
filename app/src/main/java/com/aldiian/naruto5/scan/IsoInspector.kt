package com.aldiian.naruto5.scan

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets

/**
 * Pure-Kotlin PS2 disc image inspector (no Android dependencies, unit-tested on the JVM).
 *
 * Pipeline: detect container format -> locate ISO9660 Primary Volume Descriptor -> walk the
 * root directory -> parse SYSTEM.CNF -> resolve the boot executable (BOOT2) -> verify the ELF
 * header -> check the image is not truncated. Compressed containers (CHD/CSO/ZSO) are detected
 * here and their serial is read by the native core instead (see GameScanner).
 */

/** Random-access byte source, so the same parser works on files and on SAF file descriptors. */
interface ByteSource : Closeable {
    val size: Long
    /** Reads up to [len] bytes at [pos]; returns the count read (0 at EOF). */
    fun read(pos: Long, buf: ByteArray, off: Int = 0, len: Int = buf.size): Int
}

class ChannelSource(private val channel: FileChannel, private val onClose: Closeable? = null) : ByteSource {
    override val size: Long get() = channel.size()
    override fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int {
        if (pos >= size) return 0
        val bb = ByteBuffer.wrap(buf, off, len)
        var total = 0
        var p = pos
        while (bb.hasRemaining()) {
            val n = channel.read(bb, p)
            if (n <= 0) break
            total += n
            p += n
        }
        return total
    }
    override fun close() {
        runCatching { channel.close() }
        runCatching { onClose?.close() }
    }

    companion object {
        fun open(file: File): ChannelSource {
            val raf = RandomAccessFile(file, "r")
            return ChannelSource(raf.channel, raf)
        }
    }
}

class ByteArraySource(private val data: ByteArray) : ByteSource {
    override val size: Long get() = data.size.toLong()
    override fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int {
        if (pos >= data.size) return 0
        val n = minOf(len.toLong(), data.size - pos).toInt()
        System.arraycopy(data, pos.toInt(), buf, off, n)
        return n
    }
    override fun close() {}
}

enum class ImageFormat(val label: String, val nativeOnly: Boolean) {
    ISO("ISO 9660 (2048 B/sektor)", false),
    BIN_RAW("BIN raw (2352 B/sektor)", false),
    CHD("CHD (terkompresi)", true),
    CSO("CSO (terkompresi)", true),
    ZSO("ZSO (terkompresi)", true),
    GZ("GZip ISO", true),
    ELF("PS2 ELF executable", false),
    UNKNOWN("Tidak dikenal", false),
}

enum class Severity { OK, WARNING, ERROR }

data class Finding(val severity: Severity, val message: String)

data class SystemCnf(
    val boot2: String?,
    val version: String?,
    val videoMode: String?,
    val raw: Map<String, String>,
)

data class DiscInfo(
    val format: ImageFormat,
    val sectorSize: Int = 2048,
    val volumeId: String? = null,
    val volumeBlocks: Long = 0,
    val expectedBytes: Long = 0,
    val actualBytes: Long = 0,
    val systemCnf: SystemCnf? = null,
    val bootExecutable: String? = null,
    val serial: String? = null,
    val elfFound: Boolean = false,
    val elfValid: Boolean = false,
    val elfSize: Long = 0,
    val findings: List<Finding> = emptyList(),
) {
    val hasErrors: Boolean get() = findings.any { it.severity == Severity.ERROR }
    val isComplete: Boolean
        get() = !format.nativeOnly && format != ImageFormat.UNKNOWN && !hasErrors && elfValid
}

object IsoInspector {
    const val SECTOR = 2048
    const val RAW_SECTOR = 2352
    private const val PVD_SECTOR = 16L
    private val SYNC = byteArrayOf(0, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0)

    fun inspect(file: File): DiscInfo = ChannelSource.open(file).use { inspect(it, file.name) }

    fun inspect(src: ByteSource, fileName: String = ""): DiscInfo {
        val head = ByteArray(64)
        val n = src.read(0, head)
        val format = detectFormat(head, n, fileName)
        val findings = mutableListOf<Finding>()
        when (format) {
            ImageFormat.CHD, ImageFormat.CSO, ImageFormat.ZSO, ImageFormat.GZ -> {
                findings += Finding(Severity.OK, "Container ${format.label} dikenali; isi dibaca oleh core ARMSX2.")
                return DiscInfo(format = format, actualBytes = src.size, findings = findings)
            }
            ImageFormat.ELF -> {
                val ok = isPs2Elf(src, 0)
                findings += if (ok) Finding(Severity.OK, "Header ELF MIPS valid.")
                else Finding(Severity.ERROR, "File .elf bukan executable MIPS PS2 yang valid.")
                return DiscInfo(format = format, actualBytes = src.size, elfFound = true, elfValid = ok,
                    elfSize = src.size, bootExecutable = fileName, findings = findings)
            }
            else -> {}
        }

        // Try cooked 2048-byte sectors first, then raw 2352-byte (Mode 2 Form 1 / Mode 1).
        val layouts = listOf(Layout(SECTOR, 0), Layout(RAW_SECTOR, 24), Layout(RAW_SECTOR, 16))
        val layout = layouts.firstOrNull { hasPvd(src, it) }
        if (layout == null) {
            findings += Finding(Severity.ERROR, "Primary Volume Descriptor ISO 9660 tidak ditemukan (bukan image disc PS2 yang valid atau file rusak).")
            return DiscInfo(format = ImageFormat.UNKNOWN, actualBytes = src.size, findings = findings)
        }
        val fmt = if (layout.size == SECTOR) ImageFormat.ISO else ImageFormat.BIN_RAW
        val reader = SectorReader(src, layout)
        val pvd = reader.sector(PVD_SECTOR)
        val volumeId = String(pvd, 40, 32, StandardCharsets.US_ASCII).trim()
        val volumeBlocks = le32(pvd, 80)
        val blockSize = le16(pvd, 128)
        if (blockSize != SECTOR) findings += Finding(Severity.WARNING, "Logical block size $blockSize (diharapkan 2048).")
        val expected = volumeBlocks * layout.size
        if (src.size < expected) {
            findings += Finding(Severity.ERROR,
                "Image terpotong: ukuran ${src.size} B, volume ISO butuh $expected B. Salin ulang file game.")
        } else {
            findings += Finding(Severity.OK, "Ukuran image sesuai volume ISO ($volumeBlocks sektor).")
        }

        val root = DirEntry("", le32(pvd, 156 + 2), le32(pvd, 156 + 10), true)
        val rootEntries = runCatching { reader.listDir(root) }.getOrElse {
            findings += Finding(Severity.ERROR, "Direktori root tidak bisa dibaca: ${it.message}")
            emptyList()
        }
        val cnfEntry = rootEntries.firstOrNull { !it.isDir && it.name.equals("SYSTEM.CNF", true) }
        if (cnfEntry == null) {
            findings += Finding(Severity.ERROR, "SYSTEM.CNF tidak ada di root disc — bukan disc PS2 atau image rusak.")
            return DiscInfo(fmt, layout.size, volumeId, volumeBlocks, expected, src.size, findings = findings)
        }
        val cnfBytes = reader.readFile(cnfEntry, maxBytes = 4096)
        val cnf = parseSystemCnf(String(cnfBytes, StandardCharsets.ISO_8859_1))
        val boot = cnf.boot2
        if (boot == null) {
            val hasBoot1 = cnf.raw.containsKey("BOOT")
            findings += Finding(Severity.ERROR,
                if (hasBoot1) "SYSTEM.CNF berisi BOOT (disc PS1), bukan BOOT2 PS2." else "SYSTEM.CNF tidak berisi BOOT2.")
            return DiscInfo(fmt, layout.size, volumeId, volumeBlocks, expected, src.size, cnf, findings = findings)
        }
        findings += Finding(Severity.OK, "SYSTEM.CNF: BOOT2 = $boot")
        val serial = serialFromBootPath(boot)
        if (serial == null) findings += Finding(Severity.WARNING, "Serial tidak dapat diturunkan dari \"$boot\".")

        // Resolve the executable path inside the image.
        val parts = bootPathComponents(boot)
        var dirEntries = rootEntries
        var elfEntry: DirEntry? = null
        for ((i, part) in parts.withIndex()) {
            val match = dirEntries.firstOrNull { it.name.equals(part, true) }
            if (match == null) break
            if (i == parts.lastIndex) elfEntry = match.takeIf { !it.isDir }
            else dirEntries = runCatching { reader.listDir(match) }.getOrDefault(emptyList())
        }
        var elfValid = false
        if (elfEntry == null) {
            findings += Finding(Severity.ERROR, "Executable ${parts.joinToString("/")} tidak ditemukan di disc.")
        } else {
            val elfHead = reader.readFile(elfEntry, maxBytes = 64)
            elfValid = isPs2ElfHeader(elfHead)
            val endByte = (elfEntry.lba + (elfEntry.size + SECTOR - 1) / SECTOR) * layout.size
            when {
                endByte > src.size -> findings += Finding(Severity.ERROR, "Executable melewati akhir file — image tidak lengkap.")
                !elfValid -> findings += Finding(Severity.ERROR, "Executable ${elfEntry.name} bukan ELF MIPS PS2 yang valid.")
                else -> findings += Finding(Severity.OK, "Executable ${elfEntry.name} valid (${elfEntry.size} B).")
            }
        }
        return DiscInfo(
            format = fmt, sectorSize = layout.size, volumeId = volumeId, volumeBlocks = volumeBlocks,
            expectedBytes = expected, actualBytes = src.size, systemCnf = cnf, bootExecutable = boot,
            serial = serial, elfFound = elfEntry != null, elfValid = elfValid, elfSize = elfEntry?.size ?: 0,
            findings = findings,
        )
    }

    fun detectFormat(head: ByteArray, n: Int, fileName: String): ImageFormat {
        fun magic(s: String) = n >= s.length && String(head, 0, s.length, StandardCharsets.ISO_8859_1) == s
        return when {
            magic("MComprHD") -> ImageFormat.CHD
            magic("CISO") -> ImageFormat.CSO
            magic("ZISO") -> ImageFormat.ZSO
            n >= 2 && head[0] == 0x1F.toByte() && head[1] == 0x8B.toByte() -> ImageFormat.GZ
            n >= 4 && head[0] == 0x7F.toByte() && magic("\u007FELF") -> ImageFormat.ELF
            fileName.endsWith(".bin", true) || (n >= 12 && head.copyOfRange(0, 12).contentEquals(SYNC)) -> ImageFormat.BIN_RAW
            else -> ImageFormat.ISO
        }
    }

    /** Parses SYSTEM.CNF "KEY = value" lines (keys upper-cased, whitespace-tolerant). */
    fun parseSystemCnf(text: String): SystemCnf {
        val map = linkedMapOf<String, String>()
        text.split('\n', '\r').forEach { line ->
            val eq = line.indexOf('=')
            if (eq > 0) {
                val k = line.substring(0, eq).trim().uppercase()
                val v = line.substring(eq + 1).trim()
                if (k.isNotEmpty()) map[k] = v
            }
        }
        return SystemCnf(map["BOOT2"], map["VER"], map["VMODE"], map)
    }

    /** "cdrom0:\SLES_556.05;1" -> ["SLES_556.05"]; handles sub-directories, drops ";1". */
    fun bootPathComponents(boot2: String): List<String> {
        var p = boot2.trim()
        val colon = p.indexOf(':')
        if (colon >= 0) p = p.substring(colon + 1)
        return p.split('\\', '/').map { normalizeName(it) }.filter { it.isNotBlank() }
    }

    /** ISO 9660 identifiers carry a ";1" version and sometimes a trailing '.'. */
    fun normalizeName(raw: String): String = raw.substringBefore(';').trim().trimEnd('.')

    /** "cdrom0:\SLES_556.05;1" -> "SLES-55605". Returns null if it does not look like a serial. */
    fun serialFromBootPath(boot2: String): String? {
        val file = bootPathComponents(boot2).lastOrNull() ?: return null
        val m = Regex("""^([A-Za-z]{4})[_-](\d{3})\.(\d{2})$""").find(file) ?: return null
        return "${m.groupValues[1].uppercase()}-${m.groupValues[2]}${m.groupValues[3]}"
    }

    fun isPs2ElfHeader(h: ByteArray): Boolean {
        if (h.size < 20) return false
        val magic = h[0] == 0x7F.toByte() && h[1] == 'E'.code.toByte() && h[2] == 'L'.code.toByte() && h[3] == 'F'.code.toByte()
        val elf32 = h[4] == 1.toByte()
        val little = h[5] == 1.toByte()
        val machine = (h[18].toInt() and 0xFF) or ((h[19].toInt() and 0xFF) shl 8)
        return magic && elf32 && little && machine == 8 /* EM_MIPS */
    }

    private fun isPs2Elf(src: ByteSource, pos: Long): Boolean {
        val b = ByteArray(64)
        val n = src.read(pos, b)
        return n >= 20 && isPs2ElfHeader(b)
    }

    private data class Layout(val size: Int, val dataOffset: Int)

    private fun hasPvd(src: ByteSource, l: Layout): Boolean {
        val b = ByteArray(8)
        val pos = PVD_SECTOR * l.size + l.dataOffset
        if (src.read(pos, b) < 6) return false
        if (l.size == RAW_SECTOR) {
            val sync = ByteArray(12)
            src.read(PVD_SECTOR * l.size, sync)
            if (!sync.contentEquals(SYNC)) return false
        }
        return b[0] == 1.toByte() && String(b, 1, 5, StandardCharsets.US_ASCII) == "CD001"
    }

    private data class DirEntry(val name: String, val lba: Long, val size: Long, val isDir: Boolean)

    private class SectorReader(val src: ByteSource, val l: Layout) {
        fun sector(lba: Long): ByteArray {
            val b = ByteArray(SECTOR)
            val n = src.read(lba * l.size + l.dataOffset, b)
            if (n < SECTOR) throw IllegalStateException("sektor $lba di luar file")
            return b
        }

        fun readFile(e: DirEntry, maxBytes: Int): ByteArray {
            val want = minOf(e.size, maxBytes.toLong()).toInt()
            val out = ByteArray(want)
            var done = 0
            var lba = e.lba
            while (done < want) {
                val s = sector(lba++)
                val c = minOf(SECTOR, want - done)
                System.arraycopy(s, 0, out, done, c)
                done += c
            }
            return out
        }

        fun listDir(dir: DirEntry): List<DirEntry> {
            val out = mutableListOf<DirEntry>()
            val sectors = ((dir.size + SECTOR - 1) / SECTOR).coerceIn(1, 64)
            for (i in 0 until sectors) {
                val s = sector(dir.lba + i)
                var p = 0
                while (p < SECTOR) {
                    val len = s[p].toInt() and 0xFF
                    if (len == 0) break
                    if (p + 33 > SECTOR) break
                    val nameLen = s[p + 32].toInt() and 0xFF
                    if (p + 33 + nameLen > SECTOR) break
                    val flags = s[p + 25].toInt()
                    val isDir = flags and 0x02 != 0
                    val nameBytes = s.copyOfRange(p + 33, p + 33 + nameLen)
                    val special = nameLen == 1 && (nameBytes[0] == 0.toByte() || nameBytes[0] == 1.toByte())
                    if (!special) {
                        out += DirEntry(normalizeName(String(nameBytes, StandardCharsets.ISO_8859_1)), le32(s, p + 2), le32(s, p + 10), isDir)
                    }
                    p += len
                }
            }
            return out
        }
    }

    internal fun le32(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or
            ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)

    internal fun le16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
}
