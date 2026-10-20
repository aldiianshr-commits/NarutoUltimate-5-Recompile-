#!/usr/bin/env python3
"""Writes a 4 MiB *structurally valid but empty* PS2 BIOS image for CI smoke tests only.

It contains just a ROMDIR (RESET, ROMDIR, EXTINFO, ROMVER) so the core's BIOS parser
(LoadBiosVersion in core/pcsx2/ps2/BiosTools.cpp) accepts it; every other byte is zero.
It holds no Sony code and cannot boot anything — it only lets the test drive the VM boot path.
"""
import struct, sys

def entry(name, size):
    return name.ljust(10, b'\0') + struct.pack('<HI', 0, size)

img = bytearray(4 * 1024 * 1024)
romdir_off = 0x2700
romver = b'0230EC20040614'            # v02.30, Europe, Console, 2004-06-14
entries = [entry(b'RESET', romdir_off), entry(b'ROMDIR', 0x50), entry(b'EXTINFO', 0x10),
           entry(b'ROMVER', len(romver)), entry(b'', 0)]
rd = b''.join(entries)
img[romdir_off:romdir_off + len(rd)] = rd
extinfo_off = romdir_off + 0x50
romver_off = extinfo_off + 0x10
img[extinfo_off + 0x10 - 0x10: extinfo_off + 0x10] = b'SMOKE-TEST-ONLY'.ljust(16, b'\0')
img[romver_off:romver_off + len(romver)] = romver
open(sys.argv[1], 'wb').write(img)
