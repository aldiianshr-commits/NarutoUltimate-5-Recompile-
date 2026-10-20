#!/usr/bin/env python3
"""Builds a tiny, structurally valid PS2 disc image for smoke tests.

ISO 9660 with SYSTEM.CNF (BOOT2 = cdrom0:\\SLES_556.05;1) and a MIPS ELF header, i.e. what the
launcher's scanner and the core's serial reader see on a real Naruto UN5 (PAL) disc. It contains
no game data and cannot boot.
"""
import struct, sys

S = 2048

def both32(v): return struct.pack('<I', v) + struct.pack('>I', v)
def both16(v): return struct.pack('<H', v) + struct.pack('>H', v)

def dirrec(lba, size, is_dir, name):
    ln = 33 + len(name)
    if ln % 2: ln += 1
    r = bytearray(ln)
    r[0] = ln
    r[2:10] = both32(lba); r[10:18] = both32(size)
    r[25] = 2 if is_dir else 0
    r[28:32] = both16(1)
    r[32] = len(name); r[33:33 + len(name)] = name
    return bytes(r)

def build(serial_file=b'SLES_556.05;1', cnf=b'BOOT2 = cdrom0:\\SLES_556.05;1\r\nVER = 1.00\r\nVMODE = PAL\r\n'):
    elf = bytearray(6000)
    elf[0:4] = b'\x7fELF'; elf[4] = 1; elf[5] = 1; elf[6] = 1; elf[16] = 2; elf[18] = 8
    root, cnf_lba, elf_lba = 18, 19, 20
    total = elf_lba + (len(elf) + S - 1) // S + 8
    img = bytearray(total * S)
    p = 16 * S
    img[p] = 1; img[p+1:p+6] = b'CD001'; img[p+6] = 1
    img[p+40:p+72] = b'NARUTO5_SMOKE'.ljust(32)
    img[p+80:p+88] = both32(total)
    img[p+128:p+132] = both16(S)
    img[p+156:p+156+34] = dirrec(root, S, True, b'\x00')
    img[17*S] = 255; img[17*S+1:17*S+6] = b'CD001'
    q = root * S
    for rec in (dirrec(root, S, True, b'\x00'), dirrec(root, S, True, b'\x01'),
                dirrec(cnf_lba, len(cnf), False, b'SYSTEM.CNF;1'), dirrec(elf_lba, len(elf), False, serial_file)):
        img[q:q+len(rec)] = rec; q += len(rec)
    img[cnf_lba*S:cnf_lba*S+len(cnf)] = cnf
    img[elf_lba*S:elf_lba*S+len(elf)] = elf
    return bytes(img)

if __name__ == '__main__':
    open(sys.argv[1], 'wb').write(build())
