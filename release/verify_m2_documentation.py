#!/usr/bin/env python3
"""Verify that every alpha.5 distribution contains the M2 test contract."""

from __future__ import annotations

import pathlib
import sys
import zipfile

EXPECTED_VERSION = "0.1.0-alpha.5"
STALE_FRAGMENTS = (
    "0.1.0-alpha.4",
    "no gameplay, map loading, teams, ready state, countdown",
    "bitmap font also limits Polish UI text to ASCII",
    "Brak gameplayu, mapy, drużyn, walki",
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def require_fragments(label: str, text: str, fragments: tuple[str, ...]) -> None:
    for fragment in fragments:
        require(fragment in text, f"{label} is missing required fragment: {fragment}")
    for stale in STALE_FRAGMENTS:
        require(stale not in text, f"{label} contains stale release text: {stale}")


def read_archive_text(archive_path: pathlib.Path, entry: str) -> str:
    require(archive_path.is_file(), f"missing archive: {archive_path.name}")
    require(not archive_path.is_symlink(), f"archive is a symbolic link: {archive_path.name}")
    with zipfile.ZipFile(archive_path) as archive:
        try:
            payload = archive.read(entry)
        except KeyError as failure:
            raise ValueError(f"{archive_path.name} is missing {entry}") from failure
    return payload.decode("utf-8")


def main() -> int:
    if len(sys.argv) != 3:
        print(
            "usage: verify_m2_documentation.py <release-dir> <version>",
            file=sys.stderr,
        )
        return 2

    release_directory = pathlib.Path(sys.argv[1]).resolve()
    version = sys.argv[2]
    require(version == EXPECTED_VERSION, f"unexpected M2 release version: {version}")
    require(release_directory.is_dir(), "release directory is missing")
    require(not release_directory.is_symlink(), "release directory must not be a symbolic link")

    client_name = f"sunderfront-client-{version}.zip"
    server_name = f"sunderfront-server-{version}.zip"
    windows_name = f"sunderfront-client-windows-x64-{version}.zip"
    client_root = f"sunderfront-client-{version}"
    server_root = f"sunderfront-server-{version}"
    windows_root = f"sunderfront-client-windows-x64-{version}"

    documents = {
        "JVM client English guide": (
            read_archive_text(
                release_directory / client_name,
                f"{client_root}/README.md",
            ),
            (
                "Interactive Lobby Alpha",
                "M2 lobby and preparation test",
                "two ready players",
                "`PREPARATION`",
                "Known limitations",
            ),
        ),
        "JVM client Polish guide": (
            read_archive_text(
                release_directory / client_name,
                f"{client_root}/README-PL.txt",
            ),
            (
                "INTERAKTYWNE LOBBY M2",
                "TEST M2: LOBBY I PREPARATION",
                "dwóch gotowych graczy",
                "PREPARATION",
                "OGRANICZENIA",
            ),
        ),
        "server English guide": (
            read_archive_text(
                release_directory / server_name,
                f"{server_root}/README.md",
            ),
            (
                "Interactive Lobby Alpha",
                "M2 authoritative lobby test",
                "minimum of two ready players",
                "`PREPARATION`",
                "Known limitations",
            ),
        ),
        "server Polish guide": (
            read_archive_text(
                release_directory / server_name,
                f"{server_root}/README-PL.txt",
            ),
            (
                "SERWER INTERAKTYWNEGO LOBBY M2",
                "TEST M2: AUTORYTATYWNE LOBBY",
                "minimum dwóch gotowych graczy",
                "PREPARATION",
                "OGRANICZENIA",
            ),
        ),
        "Windows client English guide": (
            read_archive_text(
                release_directory / windows_name,
                f"{windows_root}/README.md",
            ),
            (
                "Interactive Lobby Alpha",
                "M2 test",
                "two ready players",
                "`PREPARATION`",
                "Known limitations",
            ),
        ),
        "Windows client Polish guide": (
            read_archive_text(
                release_directory / windows_name,
                f"{windows_root}/README-PL.txt",
            ),
            (
                "INTERAKTYWNE LOBBY M2",
                "TEST M2",
                "dwóch gotowych graczy",
                "PREPARATION",
                "OGRANICZENIA",
            ),
        ),
    }
    for label, (text, fragments) in documents.items():
        require_fragments(label, text, fragments)

    source_root = pathlib.Path(__file__).resolve().parents[1]
    release_notes = (source_root / "release" / "RELEASE_NOTES.md").read_text(encoding="utf-8")
    require_fragments(
        "GitHub release notes",
        release_notes,
        (
            "Sunderfront v0.1.0-alpha.5 — Interactive Lobby Alpha",
            "M2 test procedure",
            "two ready players",
            "Known limitations",
            windows_name,
            client_name,
            server_name,
            "SHA256SUMS",
        ),
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, UnicodeDecodeError, ValueError, zipfile.BadZipFile) as failure:
        print(f"M2 documentation verification failed: {failure}", file=sys.stderr)
        raise SystemExit(1) from None
