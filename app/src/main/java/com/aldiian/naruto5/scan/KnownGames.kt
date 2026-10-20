package com.aldiian.naruto5.scan

/**
 * Releases of Naruto Shippuden: Ultimate Ninja 5 this launcher is tuned for. Serials and
 * per-game fixes come from the PCSX2/ARMSX2 GameDB (core/bin/resources/GameIndex.yaml), which
 * the core applies automatically at boot (half-pixel offset, native scaling, VU clamp modes).
 */
object KnownGames {
    enum class Match { TARGET, REGIONAL_VARIANT, OTHER_GAME, UNKNOWN }

    data class Release(val serial: String, val title: String, val region: String, val note: String)

    val releases = listOf(
        Release("SLES-55605", "Naruto Shippuden: Ultimate Ninja 5", "PAL (Eropa/Australia, 5 bahasa)",
            "Target utama. GameDB: status Playable, patch widescreen 16:9 tersedia."),
        Release("SLPS-25837", "Naruto Shippuuden: Narutimate Accel 2", "NTSC-J",
            "Versi Jepang dari Ultimate Ninja 5 (teks Jepang)."),
        Release("SLPM-68019", "Narutimate Accel 2 – Zen 60-nin Soku Kaihou Disc", "NTSC-J",
            "Disc promo Jepang (semua karakter terbuka)."),
    )

    /** Serial whose widescreen 16:9 pnach ships in the core's patches.zip. */
    val widescreenPatchSerials = setOf("SLES-55605")

    fun find(serial: String?): Release? = releases.firstOrNull { it.serial.equals(serial, true) }

    fun classify(serial: String?): Match = when {
        serial.isNullOrBlank() -> Match.UNKNOWN
        serial.equals(releases[0].serial, true) -> Match.TARGET
        find(serial) != null -> Match.REGIONAL_VARIANT
        else -> Match.OTHER_GAME
    }

    /** PCSX2 GameDatabase::Compatibility values. */
    fun compatLabel(value: Int): String = when (value) {
        1 -> "Tidak jalan"
        2 -> "Hanya intro"
        3 -> "Sampai menu"
        4 -> "Masuk gameplay (ada masalah)"
        5 -> "Playable"
        6 -> "Sempurna"
        else -> "Belum diketahui"
    }
}
