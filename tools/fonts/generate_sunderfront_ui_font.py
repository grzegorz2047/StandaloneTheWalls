#!/usr/bin/env python3
"""Generate the deterministic Sunderfront UI BMFont from a pinned Andika source."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

from fontTools.ttLib import TTFont
from PIL import Image, ImageDraw, ImageFont

EXPECTED_SOURCE_SHA256 = "694d12d0f3fb2be696dbbde93eee3ccbdee766751d836eb1fbe8aab2d439d38a"
FONT_SIZE = 48
PADDING = 2
SPACING = 1
ATLAS_WIDTH = 1024
FONT_FACE = "SunderfrontUI-Regular"
PNG_NAME = "SunderfrontUI-Regular.png"
REQUIRED_SYMBOLS = "–—…„”’←→✓✗°×"


def charset() -> list[int]:
    values = set(range(0x20, 0x7F))
    values.update(map(ord, "ĄĆĘŁŃÓŚŹŻąćęłńóśźż" + REQUIRED_SYMBOLS))
    return sorted(values)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def next_power_of_two(value: int) -> int:
    return 1 if value <= 1 else 1 << (value - 1).bit_length()


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate Sunderfront's deterministic BMFont atlas"
    )
    parser.add_argument("--source-font", required=True, type=Path)
    parser.add_argument("--output-directory", required=True, type=Path)
    args = parser.parse_args()

    source = args.source_font.resolve()
    if sha256(source) != EXPECTED_SOURCE_SHA256:
        raise SystemExit("unexpected Andika source SHA-256")

    tt_font = TTFont(source, lazy=True)
    supported: set[int] = set()
    for table in tt_font["cmap"].tables:
        supported.update(table.cmap)
    requested = charset()
    missing = [value for value in requested if value not in supported]
    if missing:
        raise SystemExit(
            "source font lacks required codepoints: "
            + ", ".join(f"U+{value:04X}" for value in missing)
        )

    font = ImageFont.truetype(
        str(source), FONT_SIZE, layout_engine=ImageFont.Layout.RAQM
    )
    ascent, descent = font.getmetrics()
    line_height = ascent + descent + 2 * PADDING

    glyphs: list[dict[str, int | str]] = []
    x = SPACING
    y = SPACING
    row_height = 0
    for codepoint in requested:
        character = chr(codepoint)
        x0, y0, x1, y1 = map(int, font.getbbox(character, anchor="ls"))
        glyph_width = max(0, x1 - x0)
        glyph_height = max(0, y1 - y0)
        cell_width = glyph_width + 2 * PADDING if glyph_width and glyph_height else 0
        cell_height = glyph_height + 2 * PADDING if glyph_width and glyph_height else 0
        if cell_width and x + cell_width + SPACING > ATLAS_WIDTH:
            x = SPACING
            y += row_height + SPACING
            row_height = 0
        glyphs.append(
            {
                "id": codepoint,
                "char": character,
                "x": x if cell_width else 0,
                "y": y if cell_height else 0,
                "width": cell_width,
                "height": cell_height,
                "xoffset": x0 - PADDING if cell_width else 0,
                "yoffset": ascent + y0 - PADDING if cell_height else 0,
                "xadvance": int(round(font.getlength(character))),
                "bbox_x0": x0,
                "bbox_y0": y0,
            }
        )
        if cell_width:
            x += cell_width + SPACING
            row_height = max(row_height, cell_height)

    atlas_height = next_power_of_two(y + row_height + SPACING)
    atlas = Image.new("RGBA", (ATLAS_WIDTH, atlas_height), (255, 255, 255, 0))
    draw = ImageDraw.Draw(atlas)
    for glyph in glyphs:
        if glyph["width"] == 0:
            continue
        baseline_x = int(glyph["x"]) + PADDING - int(glyph["bbox_x0"])
        baseline_y = int(glyph["y"]) + PADDING - int(glyph["bbox_y0"])
        draw.text(
            (baseline_x, baseline_y),
            str(glyph["char"]),
            font=font,
            fill=(255, 255, 255, 255),
            anchor="ls",
        )

    output = args.output_directory.resolve()
    output.mkdir(parents=True, exist_ok=True)
    png_path = output / PNG_NAME
    atlas.save(png_path, format="PNG", optimize=False, compress_level=9)

    kerning: list[tuple[int, int, int]] = []
    for first in requested:
        first_character = chr(first)
        first_advance = font.getlength(first_character)
        for second in requested:
            second_character = chr(second)
            amount = int(
                round(
                    font.getlength(first_character + second_character)
                    - first_advance
                    - font.getlength(second_character)
                )
            )
            if amount:
                kerning.append((first, second, amount))

    fnt_lines = [
        f'info face="{FONT_FACE}" size={FONT_SIZE} bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=1 padding={PADDING},{PADDING},{PADDING},{PADDING} spacing={SPACING},{SPACING} outline=0',
        f"common lineHeight={line_height} base={ascent + PADDING} scaleW={ATLAS_WIDTH} scaleH={atlas_height} pages=1 packed=0 alphaChnl=0 redChnl=4 greenChnl=4 blueChnl=4",
        f'page id=0 file="{PNG_NAME}"',
        f"chars count={len(glyphs)}",
    ]
    for glyph in glyphs:
        fnt_lines.append(
            "char id={id} x={x} y={y} width={width} height={height} "
            "xoffset={xoffset} yoffset={yoffset} xadvance={xadvance} "
            "page=0 chnl=15".format(**glyph)
        )
    fnt_lines.append(f"kernings count={len(kerning)}")
    for first, second, amount in kerning:
        fnt_lines.append(f"kerning first={first} second={second} amount={amount}")
    fnt_path = output / "SunderfrontUI-Regular.fnt"
    fnt_path.write_text(
        "\n".join(fnt_lines) + "\n", encoding="utf-8", newline="\n"
    )

    print(f"source_sha256={sha256(source)}")
    print(f"png_sha256={sha256(png_path)}")
    print(f"fnt_sha256={sha256(fnt_path)}")
    print(f"atlas={ATLAS_WIDTH}x{atlas_height}")
    print(f"glyphs={len(glyphs)} kernings={len(kerning)}")


if __name__ == "__main__":
    main()
