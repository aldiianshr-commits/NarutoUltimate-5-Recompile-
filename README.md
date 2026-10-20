<p align="center"><img src="docs/icon-512.png" width="160" alt="NarutoUltimate 5"></p>

# NarutoUltimate 5

Launcher Android native (APK, ARM64) untuk menjalankan **Naruto Shippuden: Ultimate Ninja 5** versi
PlayStation 2 di Android, dengan target utama **Xiaomi Pad 6** (Snapdragon 870 / Adreno 650).

Game dijalankan oleh **ARMSX2** — fork PCSX2 open-source (GPL-3.0) yang punya recompiler **JIT native
ARM64** untuk EE (MIPS R5900), IOP (R3000), VU0 dan VU1 — yang dikompilasi dari source di repo ini
(submodule `core/`) dan dikendalikan lewat JNI oleh launcher ini.

> **Status jujur, baca dulu:**
> - Ini **emulasi dengan dynamic recompilation (JIT)**, **bukan static recompilation**. Lihat
>   [Backend](#backend-emulasi-jit-vs-static-recompilation).
> - APK dibangun dan diuji otomatis di GitHub Actions (tes unit, build native lengkap, smoke test di
>   emulator Android — lihat [Pengujian](#pengujian)). Saya tidak
>   punya perangkat fisik maupun file game/BIOS, jadi **boot game Naruto UN5 sungguhan belum diuji
>   oleh saya di Xiaomi Pad 6**. Core yang dipakai adalah emulator yang memang menjalankan game ini
>   (GameDB PCSX2: status *Playable* untuk SLES-55605), tetapi hasil akhir di perangkat Anda perlu
>   dicek — gunakan menu **Diagnostik › Ekspor log** bila ada masalah.

---

## Unduh & pasang

1. Buka tab **Releases** (atau **Actions › artefak build**) lalu unduh `NarutoUltimate5-<versi>-arm64.apk`.
2. Pasang APK (izinkan "instal dari sumber tak dikenal").
3. Siapkan file **milik Anda sendiri**:
   - Image game: `/storage/emulated/0/Naruto5/` — `.iso`, `.bin`, `.chd`, `.cso`, `.zso`, `.gz`.
   - BIOS PS2 hasil dump dari konsol Anda: `/storage/emulated/0/Naruto5/BIOS/` (atau tombol **Impor BIOS**).
4. Buka aplikasi → **Beri akses semua file** → aplikasi otomatis memindai, memasang BIOS, dan
   memvalidasi game → tekan **▶ PLAY**.

Aplikasi ini **tidak menyertakan** file game maupun BIOS (logo/ikon adalah gambar yang disediakan
pemilik repo).

Update: semua build ditandatangani dengan kunci yang sama (`keystore/naruto5-sideload.jks`), jadi
APK baru bisa dipasang menimpa yang lama tanpa menghapus data.

## Folder

| Lokasi | Isi |
|---|---|
| `/storage/emulated/0/Naruto5/` | Image game (dipindai hingga 2 level sub-folder) |
| `/storage/emulated/0/Naruto5/BIOS/` | BIOS (otomatis diimpor) |
| `/storage/emulated/0/Naruto5/Save/memcards/` | Memory card PS2 (`Mcd001.ps2`) |
| `/storage/emulated/0/Naruto5/Save/states/` | Save state (slot 1–3 dari menu jeda) |
| `/storage/emulated/0/Naruto5/Save/backups/` | Backup zip memory card + state |
| `Android/data/com.aldiian.naruto5/files/` | Data core: `PCSX2-Android.ini`, shader cache, resource (GameDB, shader, patch) |

Tanpa izin "Semua file", Anda bisa memilih folder game lewat **Storage Access Framework**
(*Pilih folder (SAF)*); save lalu disimpan di folder data aplikasi.

## Fitur

**Pemindaian & validasi (pipeline otomatis)** — untuk tiap file: deteksi container (ISO 2048 B,
BIN raw 2352 B Mode1/Mode2, CHD, CSO, ZSO, GZ) → baca Primary Volume Descriptor ISO 9660 → cek
ukuran terhadap volume (deteksi file terpotong) → baca `SYSTEM.CNF` → ambil `BOOT2` → turunkan
serial (mis. `SLES_556.05` → `SLES-55605`) → cari executable di disc dan validasi header ELF MIPS →
cocokkan dengan rilis UN5 yang dikenal dan status kompatibilitas GameDB core. Untuk CHD/CSO, serial
dibaca oleh pembaca native milik core.

| Serial | Rilis | Catatan |
|---|---|---|
| SLES-55605 | Naruto Shippuden: Ultimate Ninja 5 (PAL) | Target utama; patch widescreen 16:9 tersedia |
| SLPS-25837 | Narutimate Accel 2 (NTSC-J) | Versi Jepang dari UN5 |
| SLPM-68019 | Narutimate Accel 2 – Zen 60-nin Soku Kaihou Disc | Disc promo Jepang |

Perbaikan khusus game dari GameDB (half-pixel offset, native scaling, VU clamp mode, FMV hack)
diterapkan otomatis oleh core.

**Kontrol** — layar sentuh multi-touch (D-pad 8 arah, dua analog, ✕○□△, L1/L2/R1/R2, L3/R3,
Start/Select, geser antar tombol), editor posisi/ukuran/visibilitas per tombol, ukuran global,
transparansi, getar haptik. Gamepad Bluetooth/USB: pemetaan ulang tombol, analog kiri/kanan, hat
D-pad, trigger analog (tombol bertekanan PS2), rumble (motor DualShock 2 → motor gamepad atau
perangkat). Kontrol layar otomatis disembunyikan saat gamepad dipakai.

**Save** — memory card & save state di folder publik, backup zip, pulihkan (selalu membuat backup
pengaman dulu), hapus. Pengaturan grafis bisa global atau **per game** (per serial).

**Diagnostik** — status backend, info perangkat, log event launcher+emulator, log core (output
Console ARMSX2 dari logcat), catatan crash, ekspor & bagikan laporan.

## Pengaturan grafis → core

Setiap opsi menulis kunci konfigurasi PCSX2 yang nyata (lihat `settings/GraphicsSettings.kt`,
dites di `GraphicsSettingsTest`). Opsi yang tidak punya padanan di emulator dinonaktifkan, bukan
dipalsukan.

| Opsi | Nilai | Dipetakan ke |
|---|---|---|
| Renderer | Otomatis / Vulkan / OpenGL ES / Software | `EmuCore/GS Renderer` (Otomatis = Vulkan di Adreno) |
| Resolusi | 480p 1× · 720p 1.5× · 900p 2× · 1080p 2.5× | `upscale_multiplier` |
| Batas FPS | 30 / 45 / 60 | present-rate cap core (`setFpsCap`); game tetap kecepatan normal |
| Kualitas tekstur | Rendah / Sedang / Tinggi | `filter` (Nearest / Bilinear PS2 / Bilinear paksa) + `hw_mipmap` |
| Kualitas bayangan | — | **Dinonaktifkan**: bayangan dirender game, emulator tak punya opsinya |
| Anti-aliasing | Mati / FXAA | `fxaa`. **MSAA dinonaktifkan**: tidak ada di renderer HW PCSX2 |
| Anisotropic | Mati, 2×–16× | `MaxAnisotropy` |
| V-Sync | on/off | `VsyncEnable` |
| Shader cache | on/off | `DisableShaderCache` |
| Driver GPU | System / Turnip | `libadrenotools` memuat paket Turnip (Mesa) — hanya Adreno 6xx/7xx + Vulkan |
| Aspect ratio | Otomatis / 16:9 / 16:10 | `AspectRatio` (+`CustomAspectRatio=1.6`) + pnach *Widescreen 16:9* (hanya SLES-55605) |
| Fullscreen | on/off | mode immersive Android |
| Preset | Very Low → Ultra | kombinasi opsi di atas |
| Reset to Default | | default yang disetel untuk Pad 6 |

Driver Turnip bisa diunduh langsung (rilis `bmdhacks/armsx2-turnip`, build Mesa dengan patch
ARMSX2) atau diimpor dari `.zip` format adrenotools.

## Optimasi Xiaomi Pad 6

- Core dikompilasi `-O3`, LTO, **`-march=armv8.2-a+fp16+dotprod`** (Kryo 585 / Cortex-A77), tier
  kodegen yang sama dengan APK "a11+" ARMSX2.
- JIT ARM64 untuk EE/IOP/VU0/VU1 + fastmem; **MTVU** (VU1 di thread sendiri) aktif.
- Renderer Vulkan pada Adreno 650, opsi Turnip.
- Mode layar dikunci ke **60 Hz** (panel 144 Hz) + `Surface.setFrameRate` untuk frame pacing rata.
- Profil performa: *Performa* (thread emulator di core besar A77), *Seimbang* (ADPF hint),
  *Hemat Daya* (sustained performance mode, cocok dengan batas 30 FPS).
- Shader cache di disk dan di-flush saat aplikasi di-pause; `largeHeap`; emulator di proses
  terpisah (`:emu`) sehingga keluar game membebaskan seluruh memori VM.
- Resource core (GameDB, shader, font) disalin sekali per versi.

## Backend: emulasi JIT vs static recompilation

| | ARMSX2 JIT (dipakai) | Static recompilation |
|---|---|---|
| Cara kerja | Kode MIPS game diterjemahkan ke ARM64 **saat runtime** per blok, hardware PS2 (GS, VU, IOP, SPU2, DMA, CDVD) diemulasi | Seluruh executable diterjemahkan **sebelumnya** (mis. R5900 → C++/LLVM IR → ARM64) |
| Status | Berfungsi, banyak game playable | Belum ada toolchain yang mampu untuk game ini |

Hasil riset static recompilation:
- **PS2Recomp** (ran-j/PS2Recomp) — mengubah ELF PS2 menjadi C++ + runtime; masih eksperimental,
  terutama diuji dengan MSVC/x86 (jalur vektor SSE4/AVX), banyak hardware di-stub, tiap game butuh
  kerja manual (batas fungsi, override), dan belum ada game yang dinyatakan playable.
- Static recompilation tidak menghilangkan kebutuhan emulasi GS, VU microcode (VU1 menjalankan
  microprogram yang diunggah saat runtime), IOP dan SPU2 — bagian-bagian yang justru paling berat.
- LLVM bisa menjadi backend, tetapi translasi statis MIPS R5900 (128-bit MMI, delay slot, COP2,
  self-modifying/overlay code) menuntut analisis per-game yang tidak dapat diotomatiskan di perangkat.

Karena itu backend fungsional adalah ARMSX2 (emulasi + JIT). Arsitektur launcher memisahkan
`Backend` (`core/Backends.kt`): backend static recompilation sudah terdaftar sebagai *tidak
tersedia* sehingga dapat ditambahkan nanti tanpa mengubah UI, pemindai, pengaturan, atau kontrol.

## Build sendiri

Prasyarat: JDK 17, Android SDK (compileSdk 37), NDK `29.0.14206865`, CMake `3.31.6`, Rust dengan
target `aarch64-linux-android`, Python 3.

```bash
git clone --recurse-submodules --shallow-submodules https://github.com/aldiianshr-commits/NarutoUltimate-5-Recompile-.git
cd NarutoUltimate-5-Recompile-
python3 core/platforms/android/app/src/main/cpp/3rdparty/shaderc/utils/git-sync-deps
rustup target add aarch64-linux-android
./gradlew :app:testDebugUnitTest          # tes unit (tanpa perangkat)
./gradlew :app:assembleRelease            # APK lengkap dengan core native (~20 menit pertama kali di runner GitHub, ~13 menit dengan ccache)
```

- `-Pnaruto5.skipNative=true` — kompilasi UI saja (APK tidak bisa menjalankan game).
- `-Pnaruto5.march=armv8-a` — build untuk perangkat ARMv8.0 lama (Cortex-A53).
- `-Pnaruto5.hostPageSize=0x4000` — perangkat dengan page size 16 KB.
- `-Pnaruto5.ccache=true` — pakai ccache.

CI (`.github/workflows/build-apk.yml`): job *tests* (tes unit + kompilasi app) dan job *apk*
(core native + APK release bertanda tangan, artefak, dan GitHub Release otomatis di `main`).

## Pengujian

| Lapisan | Apa yang dites | Di mana |
|---|---|---|
| Unit (JVM) | Parser ISO 9660 (ISO 2048 & BIN 2352), `SYSTEM.CNF`, serial, ELF, deteksi file terpotong, PS1, CHD/CSO/ZSO; pemetaan pengaturan → kunci core; preset; tata letak sentuh; pemetaan gamepad; editor INI | `app/src/test`, job CI *tests* |
| Build | Kompilasi core ARMSX2 penuh dengan NDK + APK release bertanda tangan | job CI *apk* |
| Perangkat (emulator Android 14 x86_64 + translasi ARM64) | APK terpasang; `libemucore_4k.so` termuat; `NativeApp.initialize` + resource core; ISO sintetis terdeteksi `SLES-55605`, valid, dikenal GameDB core; BIOS palsu ditolak parser core; folder memcard/state core = `/storage/emulated/0/Naruto5/Save`; screenshot semua tab; fase boot VM (PLAY → `EmulationActivity` → `runVMThread`) dicatat sebagai info | `tools/smoke-test.sh`, job CI *smoke*; hasil + screenshot dilampirkan di setiap GitHub Release |

Tes perangkat memakai image dan BIOS **sintetis** (`tools/make_test_iso.py`, `tools/make_test_bios.py`),
bukan data Sony/Bandai Namco, sehingga tidak bisa membuktikan gameplay.

## Struktur kode

```
app/src/main/java/com/aldiian/naruto5/
  scan/       IsoInspector (ISO9660/SYSTEM.CNF/ELF), GameScanner (file & SAF), KnownGames
  core/       CoreRuntime (resource, folder, pengaturan → core), Backends
  settings/   GraphicsSettings + CoreMapping (pemetaan ke kunci PCSX2)
  emu/        EmulationActivity (Surface, VM thread, menu jeda, save state)
  input/      PadKeys, TouchLayout, TouchControllerView, LayoutEditorPanel, GamepadMapping, GamepadInput
  gpu/        GpuInfo (EGL), TurnipManager (adrenotools)
  bios/       BiosManager (validasi oleh parser BIOS core)
  storage/    Paths, AppPrefs, SaveManager
  diag/       AppLog (event, logcat core, ekspor)
  ui/         Compose: Beranda, Grafis, Kontrol, Save, Diagnostik, editor tata letak
app/src/main/java/kr/co/iefriends/pcsx2/NativeApp.java   jembatan JNI ke libemucore (nama tetap, diikat oleh core)
core/        submodule ARMSX2 (di-pin ke commit tertentu)
```

## Batasan teknis

- Bukan static recompilation (lihat atas). Performa bergantung pada JIT ARMSX2 dan driver GPU.
- Belum diverifikasi oleh saya di Xiaomi Pad 6 dengan game asli (tidak ada perangkat/game/BIOS di
  lingkungan build). Laporkan hasil via log diagnostik.
- Hanya `arm64-v8a`, Android 10+ (API 29). Build default membutuhkan CPU ARMv8.2 (Snapdragon 845+).
- Patch widescreen hanya untuk SLES-55605; versi Jepang tetap 4:3.
- Bayangan dan MSAA tidak bisa diatur (tidak ada di emulator).
- Batas FPS 30/45 membatasi frame yang ditampilkan; logika game tetap 50/60 Hz.
- Ganti renderer/driver GPU berlaku saat game dimulai ulang.

## Lisensi

GPL-3.0-or-later (lihat `LICENSE`), karena aplikasi menautkan core ARMSX2/PCSX2 (GPL-3.0).
Kelas `org.libsdl.app` berlisensi zlib (SDL). Logo/ikon disediakan oleh pemilik repo; karakter
Naruto adalah milik pemegang hak ciptanya — jangan distribusikan ulang APK ini secara komersial.

Kredit: tim [PCSX2](https://pcsx2.net), tim [ARMSX2](https://github.com/ARMSX2/ARMSX2), Mesa/Turnip,
libadrenotools, SDL.
