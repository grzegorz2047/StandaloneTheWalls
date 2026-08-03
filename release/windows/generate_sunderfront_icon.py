#!/usr/bin/env python3
"""Generate the project-authored Sunderfront Windows icon without external tools."""

from __future__ import annotations

import binascii
import pathlib
import struct
import sys
import zlib

SIZE = 256


def chunk(kind: bytes, payload: bytes) -> bytes:
    checksum = binascii.crc32(kind)
    checksum = binascii.crc32(payload, checksum) & 0xFFFFFFFF
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", checksum)


def inside_rounded_square(x: int, y: int, inset: int, radius: int) -> bool:
    left = inset
    right = SIZE - inset - 1
    top = inset
    bottom = SIZE - inset - 1
    if left + radius <= x <= right - radius or top + radius <= y <= bottom - radius:
        return left <= x <= right and top <= y <= bottom
    center_x = left + radius if x < left + radius else right - radius
    center_y = top + radius if y < top + radius else bottom - radius
    delta_x = x - center_x
    delta_y = y - center_y
    return delta_x * delta_x + delta_y * delta_y <= radius * radius


def pixel(x: int, y: int) -> tuple[int, int, int, int]:
    if not inside_rounded_square(x, y, 10, 46):
        return (0, 0, 0, 0)

    border = not inside_rounded_square(x, y, 18, 39)
    if border:
        return (232, 190, 72, 255)

    # Dark slate background with a small vertical lift toward the top.
    lift = max(0, 28 - y // 9)
    background = (34 + lift // 3, 48 + lift // 2, 70 + lift, 255)

    # Four wall blocks evoke the separated team sectors without copying an asset.
    block = (222, 229, 237, 255)
    shadow = (154, 169, 187, 255)
    blocks = (
        (48, 57, 112, 116),
        (144, 57, 208, 116),
        (48, 140, 112, 199),
        (144, 140, 208, 199),
    )
    for left, top, right, bottom in blocks:
        if left <= x <= right and top <= y <= bottom:
            if x >= right - 7 or y >= bottom - 7:
                return shadow
            return block

    # A gold fracture separates both halves and forms a subtle S-shaped route.
    fracture_center = 128
    if y < 82:
        fracture_center -= (82 - y) // 5
    elif y < 128:
        fracture_center += (y - 82) // 4
    elif y < 174:
        fracture_center -= (y - 128) // 4
    else:
        fracture_center += (y - 174) // 5
    if abs(x - fracture_center) <= 5:
        return (245, 202, 77, 255)

    return background


def png_bytes() -> bytes:
    rows = bytearray()
    for y in range(SIZE):
        rows.append(0)  # PNG filter: None
        for x in range(SIZE):
            rows.extend(pixel(x, y))
    header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", header)
        + chunk(b"IDAT", zlib.compress(bytes(rows), level=9))
        + chunk(b"IEND", b"")
    )


def ico_bytes() -> bytes:
    image = png_bytes()
    icon_header = struct.pack("<HHH", 0, 1, 1)
    directory_entry = struct.pack("<BBBBHHII", 0, 0, 0, 0, 1, 32, len(image), 22)
    return icon_header + directory_entry + image


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: generate_sunderfront_icon.py <output.ico>", file=sys.stderr)
        return 2
    output = pathlib.Path(sys.argv[1])
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(ico_bytes())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
