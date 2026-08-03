#!/usr/bin/env python3
"""Verify immutable Direct Connect Alpha release archives and checksums."""

from __future__ import annotations

import hashlib
import json
import pathlib
import sys
import zipfile

FORBIDDEN_SUFFIXES = (
    ".pk8",
    ".der",
    ".sqlite",
    ".sqlite-wal",
    ".sqlite-shm",
    ".sfrb",
    ".sfki",
    ".sftr",
)
FORBIDDEN_PARTS = (
    "/.gradle/",
    "/cache/",
    "/player-identity",
    "/server-trust",
)
PRIVATE_MARKERS = (
    b"-----BEGIN PRIVATE KEY-----",
    b"-----BEGIN ENCRYPTED PRIVATE KEY-----",
    b"-----BEGIN OPENSSH PRIVATE KEY-----",
)
EMPTY_ASSET_LOCK = {"packs": [], "schema": 1}


def digest(path: pathlib.Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def verify_zip(path: pathlib.Path, root: str, required: set[str], client: bool) -> None:
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        require(names, f"{path.name}: empty archive")
        require(len(names) == len(set(names)), f"{path.name}: duplicate entries")
        for name in names:
            normalized = name.replace("\\", "/")
            require(not normalized.startswith("/"), f"{path.name}: absolute entry")
            require(".." not in pathlib.PurePosixPath(normalized).parts, f"{path.name}: traversal entry")
            require(
                normalized == root + "/" or normalized.startswith(root + "/"),
                f"{path.name}: unexpected archive root",
            )
            lowered = "/" + normalized.lower()
            require(
                not lowered.endswith(FORBIDDEN_SUFFIXES),
                f"{path.name}: runtime or credential file included: {normalized}",
            )
            require(
                not any(part in lowered for part in FORBIDDEN_PARTS),
                f"{path.name}: forbidden runtime path: {normalized}",
            )
            if name.endswith("/"):
                continue
            if pathlib.PurePosixPath(name).suffix.lower() not in {".jar", ".class"}:
                payload = archive.read(name)
                require(
                    not any(marker in payload for marker in PRIVATE_MARKERS),
                    f"{path.name}: private-key marker in {normalized}",
                )

        missing = sorted(required.difference(names))
        require(not missing, f"{path.name}: missing entries: {', '.join(missing)}")
        require(
            any(name.startswith(root + "/lib/") and name.endswith(".jar") for name in names),
            f"{path.name}: no runtime jars",
        )
        if client:
            lock_name = root + "/assets/assets.lock.json"
            parsed = json.loads(archive.read(lock_name).decode("utf-8"))
            require(parsed == EMPTY_ASSET_LOCK, f"{path.name}: asset lock is not empty alpha lock")
        else:
            credential_entries = [
                name
                for name in names
                if name.startswith(root + "/credentials/") and not name.endswith("/")
            ]
            require(
                credential_entries == [root + "/credentials/README.md"],
                f"{path.name}: credential directory contains generated material",
            )


def verify_checksums(release_dir: pathlib.Path, expected_files: list[pathlib.Path]) -> None:
    checksum_file = release_dir / "SHA256SUMS"
    require(checksum_file.is_file(), "SHA256SUMS is missing")
    raw_lines = checksum_file.read_text(encoding="ascii").splitlines()
    require(raw_lines == sorted(raw_lines), "SHA256SUMS is not sorted")
    parsed: dict[str, str] = {}
    for line in raw_lines:
        parts = line.split("  ", 1)
        require(len(parts) == 2, "malformed SHA256SUMS line")
        checksum, name = parts
        require(len(checksum) == 64 and checksum == checksum.lower(), "invalid SHA-256 value")
        require(name not in parsed, "duplicate SHA256SUMS entry")
        parsed[name] = checksum
    expected_names = {path.name for path in expected_files}
    require(set(parsed) == expected_names, "SHA256SUMS file set does not match archives")
    for path in expected_files:
        require(parsed[path.name] == digest(path), f"checksum mismatch: {path.name}")


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: verify_artifacts.py <release-dir> <version>", file=sys.stderr)
        return 2
    release_dir = pathlib.Path(sys.argv[1]).resolve()
    version = sys.argv[2]
    client_name = f"sunderfront-client-{version}.zip"
    server_name = f"sunderfront-server-{version}.zip"
    client_archive = release_dir / client_name
    server_archive = release_dir / server_name
    require(client_archive.is_file(), f"missing {client_name}")
    require(server_archive.is_file(), f"missing {server_name}")

    client_root = f"sunderfront-client-{version}"
    server_root = f"sunderfront-server-{version}"
    verify_zip(
        client_archive,
        client_root,
        {
            client_root + "/README.md",
            client_root + "/assets/assets.lock.json",
            client_root + "/bin/sunderfront-client",
            client_root + "/bin/sunderfront-client.bat",
            client_root + "/bin/sunderfront-direct-connect-smoke",
            client_root + "/bin/sunderfront-direct-connect-smoke.bat",
        },
        True,
    )
    verify_zip(
        server_archive,
        server_root,
        {
            server_root + "/README.md",
            server_root + "/config/server.properties",
            server_root + "/config/identity.properties",
            server_root + "/config/tls.properties",
            server_root + "/data/README.md",
            server_root + "/credentials/README.md",
            server_root + "/bin/sunderfront-server",
            server_root + "/bin/sunderfront-server.bat",
            server_root + "/bin/sunderfront-server-credentials",
            server_root + "/bin/sunderfront-server-credentials.bat",
        },
        False,
    )
    verify_checksums(release_dir, [client_archive, server_archive])
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, zipfile.BadZipFile, json.JSONDecodeError) as failure:
        print(f"release verification failed: {failure}", file=sys.stderr)
        raise SystemExit(1) from None
