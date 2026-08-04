#!/usr/bin/env python3
"""Download the pinned Andika release from GitHub and generate CI font inputs."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

RELEASE_API = "https://api.github.com/repos/silnrsi/font-andika/releases/tags/v6.200"
EXPECTED_SOURCE_SHA256 = "694d12d0f3fb2be696dbbde93eee3ccbdee766751d836eb1fbe8aab2d439d38a"
EXPECTED_ATLAS_SHA256 = "44721d69ff470c19e9ae10809a3242434fb903644c7d0aa9375223fbd970b385"
EXPECTED_METADATA_SHA256 = "d948290489c23fac65273f7e431d9bd2d345647a715000daa262479a2b807c94"
MAX_DOWNLOAD_BYTES = 100 * 1024 * 1024
USER_AGENT = "StandaloneTheWalls-ui-font-builder/1"


def sha256_bytes(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def request(url: str, accept: str | None = None) -> urllib.request.Request:
    headers = {"User-Agent": USER_AGENT}
    if accept:
        headers["Accept"] = accept
    return urllib.request.Request(url, headers=headers)


def read_bounded(url: str, accept: str | None = None) -> bytes:
    with urllib.request.urlopen(request(url, accept), timeout=120) as response:
        content_length = response.headers.get("Content-Length")
        if content_length and int(content_length) > MAX_DOWNLOAD_BYTES:
            raise SystemExit(f"download is too large: {content_length} bytes")
        chunks: list[bytes] = []
        total = 0
        while True:
            block = response.read(1024 * 1024)
            if not block:
                break
            total += len(block)
            if total > MAX_DOWNLOAD_BYTES:
                raise SystemExit("download exceeded the 100 MiB safety limit")
            chunks.append(block)
        return b"".join(chunks)


def release_archive_url() -> str:
    payload = json.loads(
        read_bounded(RELEASE_API, "application/vnd.github+json").decode("utf-8")
    )
    assets = payload.get("assets")
    if not isinstance(assets, list):
        raise SystemExit("GitHub release response does not contain an assets list")

    exact = [asset for asset in assets if asset.get("name") == "Andika-6.200.zip"]
    if len(exact) == 1:
        return str(exact[0]["browser_download_url"])

    candidates = [
        asset
        for asset in assets
        if isinstance(asset.get("name"), str)
        and asset["name"].lower().endswith(".zip")
        and "andika" in asset["name"].lower()
        and "web" not in asset["name"].lower()
    ]
    if len(candidates) != 1:
        names = ", ".join(str(asset.get("name")) for asset in assets)
        raise SystemExit(f"cannot select the Andika desktop archive; assets: {names}")
    return str(candidates[0]["browser_download_url"])


def extract_pinned_font(archive_bytes: bytes, destination: Path) -> Path:
    archive_path = destination / "andika.zip"
    archive_path.write_bytes(archive_bytes)
    with zipfile.ZipFile(archive_path) as archive:
        matches: list[tuple[str, bytes]] = []
        for member in archive.infolist():
            if member.is_dir() or Path(member.filename).name != "Andika-Regular.ttf":
                continue
            content = archive.read(member)
            if sha256_bytes(content) == EXPECTED_SOURCE_SHA256:
                matches.append((member.filename, content))
    if len(matches) != 1:
        found = ", ".join(name for name, _ in matches) or "none"
        raise SystemExit(f"expected exactly one SHA-matching Andika-Regular.ttf; found {found}")
    font_path = destination / "Andika-Regular.ttf"
    font_path.write_bytes(matches[0][1])
    return font_path


def compare_metadata(generated: Path, committed: Path) -> None:
    if sha256_file(generated) != EXPECTED_METADATA_SHA256:
        raise SystemExit(f"unexpected generated metadata SHA-256: {sha256_file(generated)}")
    if generated.read_bytes() != committed.read_bytes():
        raise SystemExit(f"generated metadata differs from committed {committed.name}")


def main() -> None:
    repository_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(
        description="Prepare deterministic UI font chunks for GitHub Actions"
    )
    parser.add_argument(
        "--output-directory",
        type=Path,
        default=repository_root / "build" / "generated-ui-font",
    )
    args = parser.parse_args()
    output = args.output_directory.resolve()
    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True)

    archive_url = release_archive_url()
    with tempfile.TemporaryDirectory(prefix="sunderfront-font-") as temporary:
        temporary_path = Path(temporary)
        source_font = extract_pinned_font(read_bounded(archive_url), temporary_path)
        subprocess.run(
            [
                sys.executable,
                str(repository_root / "tools" / "fonts" / "generate_sunderfront_ui_font.py"),
                "--source-font",
                str(source_font),
                "--output-directory",
                str(output),
            ],
            check=True,
        )

    atlas = output / "SunderfrontUI-Regular.png"
    if sha256_file(atlas) != EXPECTED_ATLAS_SHA256:
        raise SystemExit(f"unexpected generated atlas SHA-256: {sha256_file(atlas)}")

    resources = repository_root / "client" / "src" / "main" / "resources" / "Interface" / "Fonts"
    compare_metadata(output / "Default.fnt", resources / "Default.fnt")
    compare_metadata(
        output / "SunderfrontUI-Regular.fnt",
        resources / "SunderfrontUI-Regular.fnt",
    )

    expected_chunks = [
        output / "SunderfrontUI-Regular.png.b64",
        *[
            output / f"SunderfrontUI-Regular.png.b64.{index:02d}"
            for index in range(1, 6)
        ],
    ]
    if any(not chunk.is_file() for chunk in expected_chunks):
        raise SystemExit("generator did not produce the expected six atlas chunks")
    unexpected = sorted(output.glob("SunderfrontUI-Regular.png.b64.*"))[5:]
    if unexpected:
        raise SystemExit(f"generator produced unexpected chunks: {unexpected}")

    encoded_length = sum(
        len(chunk.read_text(encoding="ascii").strip()) for chunk in expected_chunks
    )
    print(f"release_url={archive_url}")
    print(f"source_sha256={EXPECTED_SOURCE_SHA256}")
    print(f"atlas_sha256={EXPECTED_ATLAS_SHA256}")
    print(f"metadata_sha256={EXPECTED_METADATA_SHA256}")
    print(f"atlas_size={atlas.stat().st_size}")
    print(f"encoded_length={encoded_length}")
    print(f"output={output}")


if __name__ == "__main__":
    main()
