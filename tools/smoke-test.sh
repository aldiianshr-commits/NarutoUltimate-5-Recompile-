#!/usr/bin/env bash
# Device smoke test, run inside an Android emulator (x86_64 image with ARM64 translation).
# Installs the release APK, provides a synthetic UN5 disc + an invalid BIOS, launches the
# launcher and checks, from the app's own log lines, that:
#   - the ARMSX2 native core loads and initialises (JNI, resources, INI, GameDB),
#   - the scanner finds and validates the disc as SLES-55605 and the core's GameDB knows it,
#   - the core's BIOS parser rejects a non-BIOS file,
# and captures screenshots of every launcher tab. It cannot boot the game (no BIOS/game data,
# and the JIT is not meant to run under binary translation).
set -u
APK="${1:?apk}"
PKG=com.aldiian.naruto5
OUT=smoke-out
mkdir -p "$OUT"
fail=0
SUMMARY="$OUT/smoke-summary.txt"
: > "$SUMMARY"
note() { echo "$1" | tee -a "$SUMMARY"; echo "::notice title=smoke::$1"; }
check() { if grep -q -E "$2" "$OUT/logcat.txt"; then note "PASS: $1"; else note "FAIL: $1"; fail=1; fi; }

adb shell getprop ro.product.cpu.abilist | tee "$OUT/abilist.txt"
adb shell wm size 2880x1800 && adb shell wm density 320   # Xiaomi Pad 6-like 16:10 landscape
adb install -r -g "$APK" | tee "$OUT/install.txt"
adb shell appops set $PKG MANAGE_EXTERNAL_STORAGE allow

python3 tools/make_test_iso.py "$OUT/smoke.iso"
head -c 4194304 /dev/urandom > "$OUT/not-a-bios.bin"
adb shell mkdir -p /sdcard/Naruto5/BIOS
adb push "$OUT/smoke.iso" /sdcard/Naruto5/Naruto5-smoke.iso
adb push "$OUT/not-a-bios.bin" /sdcard/Naruto5/BIOS/not-a-bios.bin

adb logcat -c
adb shell am start -W -n $PKG/.ui.MainActivity
sleep 40
adb exec-out screencap -p > "$OUT/1-beranda.png"

tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
  python3 - "$OUT/ui.xml" "$1" <<'PY' | xargs -r adb shell input tap
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    if m.group(1) == sys.argv[2]:
        x1, y1, x2, y2 = map(int, m.groups()[1:])
        print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
}
i=2
for tab in Grafis Kontrol Save Diagnostik; do
  tap_text "$tab"; sleep 4
  adb exec-out screencap -p > "$OUT/$i-$(echo $tab | tr 'A-Z' 'a-z').png"; i=$((i+1))
done

adb logcat -d -v time > "$OUT/logcat.txt"
adb shell pidof $PKG > "$OUT/pid.txt" || true

echo "==== checks ===="
check "native core library loaded (no load error)" "Naruto5.*GPU:|Core diinisialisasi"
check "core initialised (NativeApp.initialize + resources)" "Core diinisialisasi"
check "scanner found the disc as SLES-55605" "Game: Naruto5-smoke.iso serial=SLES-55605"
check "disc validated as playable" "serial=SLES-55605 .*playable=true"
check "core GameDB knows UN5 (compat > 0)" "serial=SLES-55605 .*compat=[1-6]"
check "core BIOS parser rejected an invalid BIOS" "BIOS ditolak"
if grep -q "Core native gagal dimuat" "$OUT/logcat.txt"; then note "FAIL: native library failed to load"; fail=1; fi
if grep -E "FATAL EXCEPTION|Fatal signal" "$OUT/logcat.txt" | grep -q -i naruto5; then note "FAIL: crash"; fail=1; fi
if [ -s "$OUT/pid.txt" ]; then note "PASS: app still running (pid $(cat "$OUT/pid.txt"))"; else note "FAIL: app process died"; fail=1; fi
{
  echo; echo "abilist: $(cat "$OUT/abilist.txt")"
  echo "---- app log (tag Naruto5) ----"
  grep -E " I/Naruto5| W/Naruto5| E/Naruto5" "$OUT/logcat.txt" | head -60
  echo "---- core log excerpt ----"
  grep -E "ARMSX2|PCSX2|GameDB|Resources|BIOS Directory|MemoryCards Directory|Savestates Directory" "$OUT/logcat.txt" | head -40
} >> "$SUMMARY"
cat "$SUMMARY"
exit $fail
