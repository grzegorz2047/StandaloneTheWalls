#!/usr/bin/env python3
"""Create a deterministic ZIP from one Windows jpackage app-image."""

from __future__ import annotations

import pathlib
import sys
import zipfile

FIXED_TIMESTAMP = (1980, 1, 1, 0, 0, 0)
FORBIDDEN_SUFFIXES = (
    ".pk8",
    ".sqlite",
    ".sqlite-wal",
    ".sqlite-shm",
    ".sfki",
    ".sftr",
    ".sfrb",
)
FORBIDDEN_PARTS = {"data", "credentials", "cache"}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def archive_info(name: str, directory: bool, executable: bool = False) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, FIXED_TIMESTAMP)
    info.create_system = 3
    mode = 0o755 if directory or executable else 0o644
    file_type = 0o040000 if directory else 0o100000
    info.external_attr = (file_type | mode) << 16
    info.compress_type = zipfile.ZIP_DEFLATED
    return info


def write_archive(source: pathlib.Path, target: pathlib.Path, archive_root: str) -> None:
    require(source.is_dir(), "app-image source is not a directory")
    require(not source.is_symlink(), "app-image source must not be a symbolic link")
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        target.unlink()

    paths = sorted(source.rglob("*"), key=lambda path: path.relative_to(source).as_posix())
    with zipfile.ZipFile(target, "w", allowZip64=True) as archive:
        archive.writestr(archive_info(archive_root + "/", True), b"")
        for path in paths:
            relative = path.relative_to(source)
            require(not path.is_symlink(), f"symbolic link in app image: {relative}")
            lowered_parts = {part.lower() for part in relative.parts}
            require(
                not FORBIDDEN_PARTS.intersection(lowered_parts),
                f"runtime data directory in app image: {relative}",
            )
            require(
                not relative.name.lower().endswith(FORBIDDEN_SUFFIXES),
                f"runtime or credential file in app image: {relative}",
            )
            name = archive_root + "/" + relative.as_posix()
            if path.is_dir():
                archive.writestr(archive_info(name + "/", True), b"")
                continue
            require(path.is_file(), f"non-regular app-image entry: {relative}")
            executable = path.suffix.lower() in {".exe", ".dll"}
            archive.writestr(
                archive_info(name, False, executable),
                path.read_bytes(),
                compress_type=zipfile.ZIP_DEFLATED,
                compresslevel=9,
            )


def main() -> int:
    if len(sys.argv) != 4:
        print(
            "usage: package_app_image.py <source-dir> <target.zip> <archive-root>",
            file=sys.stderr,
        )
        return 2
    try:
        write_archive(pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]), sys.argv[3])
        return 0
    except (OSError, ValueError, zipfile.BadZipFile) as failure:
        print(f"Windows app-image packaging failed: {failure}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
