package com.aldiian.naruto5.core

import kr.co.iefriends.pcsx2.NativeApp

/**
 * Execution backends. The launcher is structured so a static-recompilation backend can be added
 * next to the emulator later; today only the ARMSX2 dynamic recompiler is functional.
 */
sealed class Backend(val id: String, val name: String, val kind: String, val description: String) {
    abstract val available: Boolean
    abstract val status: String

    /**
     * ARMSX2 (PCSX2 fork): the game's original MIPS R5900 / R3000 / VU code is translated to
     * ARM64 at run time by JIT recompilers (EE, IOP, VU0, VU1, with fastmem), the GS is
     * emulated on Vulkan or OpenGL ES, SPU2 audio via Oboe. This is emulation with dynamic
     * recompilation — NOT a static recompilation of the game.
     */
    object Armsx2Jit : Backend(
        id = "armsx2-jit",
        name = "ARMSX2 — dynamic recompiler ARM64",
        kind = "Emulasi + JIT (dynamic recompilation)",
        description = "EE (R5900), IOP (R3000), VU0/VU1 diterjemahkan ke ARM64 saat runtime; GS via Vulkan/OpenGL ES; SPU2 via Oboe; BIOS asli milik pengguna.",
    ) {
        override val available: Boolean get() = !NativeApp.hasNoNativeBinary
        override val status: String
            get() = if (available) "Aktif — lib${NativeApp.loadedLibrary}.so termuat" else "Tidak termuat: ${NativeApp.loadError}"
    }

    /**
     * Static recompilation (ahead-of-time R5900 -> C++/LLVM -> ARM64, PS2Recomp-style). Not
     * functional: the open-source toolchains are experimental, x86-only, need per-game manual
     * work (function boundaries, VU microcode, overlays) and have no playable title yet. Kept as
     * an explicit, disabled backend so it is never confused with the emulator.
     */
    object StaticRecomp : Backend(
        id = "static-recomp",
        name = "Static recompilation (AOT) PS2 → ARM64",
        kind = "Recompilation statis",
        description = "Belum tersedia. Toolchain open-source (mis. PS2Recomp) masih eksperimental, hanya x86, dan belum ada game yang playable; VU microcode & GS tetap butuh runtime emulasi.",
    ) {
        override val available = false
        override val status = "Tidak tersedia (lihat README: Batasan teknis)"
    }

    companion object {
        val all: List<Backend> = listOf(Armsx2Jit, StaticRecomp)
        val active: Backend get() = Armsx2Jit
    }
}
