#!/usr/bin/env python3
"""Generate and install ignored UI font chunks for a local Gradle build."""

from __future__ import annotations

from pathlib import Path
import shutil
import subprocess
import sys


def main() -> None:
    repository_root = Path(__file__).resolve().parents[2]
    generated = repository_root / "build" / "generated-ui-font"
    subprocess.run(
        [
            sys.executable,
            str(repository_root / "tools" / "fonts" / "prepare_sunderfront_ui_font.py"),
            "--output-directory",
            str(generated),
        ],
        check=True,
    )

    destination = (
        repository_root
        / "client"
        / "src"
        / "main"
        / "resources"
        / "Interface"
        / "Fonts"
    )
    destination.mkdir(parents=True, exist_ok=True)
    chunks = sorted(generated.glob("SunderfrontUI-Regular.png.b64*"))
    if len(chunks) != 6:
        raise SystemExit(f"expected six generated chunks, found {len(chunks)}")
    for chunk in chunks:
        shutil.copyfile(chunk, destination / chunk.name)
    print(f"installed {len(chunks)} ignored font chunks into {destination}")


if __name__ == "__main__":
    main()
