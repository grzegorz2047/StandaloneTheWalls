#!/usr/bin/env python3
"""Compare two app-image trees and report exact differing paths."""

from __future__ import annotations

import hashlib
import pathlib
import sys


def digest(path: pathlib.Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def inventory(root: pathlib.Path) -> dict[str, tuple[int, str]]:
    result: dict[str, tuple[int, str]] = {}
    for path in sorted(root.rglob("*")):
        if path.is_symlink():
            raise ValueError(f"symbolic link in app image: {path}")
        if path.is_file():
            relative = path.relative_to(root).as_posix()
            result[relative] = (path.stat().st_size, digest(path))
    return result


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: compare_app_images.py <first-dir> <second-dir>", file=sys.stderr)
        return 2
    first_root = pathlib.Path(sys.argv[1])
    second_root = pathlib.Path(sys.argv[2])
    if not first_root.is_dir() or not second_root.is_dir():
        print("both app-image inputs must be directories", file=sys.stderr)
        return 2

    try:
        first = inventory(first_root)
        second = inventory(second_root)
    except (OSError, ValueError) as failure:
        print(f"app-image comparison failed: {failure}", file=sys.stderr)
        return 1

    differences = []
    for name in sorted(set(first).union(second)):
        if first.get(name) != second.get(name):
            differences.append((name, first.get(name), second.get(name)))
    if not differences:
        return 0

    print("Windows app images are not reproducible:", file=sys.stderr)
    for name, first_value, second_value in differences:
        print(f"  {name}", file=sys.stderr)
        print(f"    first:  {first_value}", file=sys.stderr)
        print(f"    second: {second_value}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
