#!/usr/bin/env python3
"""Assemble the verified JVM and Windows release payload with one checksum file."""

from __future__ import annotations

import hashlib
import pathlib
import shutil
import sys


def digest(path: pathlib.Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def main() -> int:
    if len(sys.argv) != 4:
        print(
            "usage: assemble_cross_platform_release.py <release-dir> <windows-zip> <version>",
            file=sys.stderr,
        )
        return 2

    release_directory = pathlib.Path(sys.argv[1]).resolve()
    windows_source = pathlib.Path(sys.argv[2]).resolve()
    version = sys.argv[3]
    client_name = f"sunderfront-client-{version}.zip"
    server_name = f"sunderfront-server-{version}.zip"
    windows_name = f"sunderfront-client-windows-x64-{version}.zip"

    require(release_directory.is_dir(), "release directory is missing")
    require(not release_directory.is_symlink(), "release directory must not be a symbolic link")
    require(windows_source.is_file(), "Windows archive is missing")
    require(not windows_source.is_symlink(), "Windows archive must not be a symbolic link")
    require(windows_source.name == windows_name, "unexpected Windows archive name")
    require(windows_source.stat().st_size > 0, "Windows archive is empty")

    expected_jvm = [release_directory / client_name, release_directory / server_name]
    for archive in expected_jvm:
        require(archive.is_file(), f"missing JVM archive: {archive.name}")
        require(not archive.is_symlink(), f"JVM archive must not be a symbolic link: {archive.name}")
        require(archive.stat().st_size > 0, f"empty JVM archive: {archive.name}")

    existing_zip_names = sorted(path.name for path in release_directory.glob("*.zip"))
    require(
        existing_zip_names == sorted([client_name, server_name]),
        "release directory contains an unexpected ZIP before Windows assembly",
    )

    windows_target = release_directory / windows_name
    require(not windows_target.exists(), "Windows archive target already exists")
    shutil.copyfile(windows_source, windows_target)

    archives = sorted([*expected_jvm, windows_target], key=lambda path: path.name)
    checksum_lines = [f"{digest(path)}  {path.name}\n" for path in archives]
    checksum_path = release_directory / "SHA256SUMS"
    checksum_path.write_text("".join(checksum_lines), encoding="ascii", newline="\n")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError) as failure:
        print(f"cross-platform release assembly failed: {failure}", file=sys.stderr)
        raise SystemExit(1) from None
