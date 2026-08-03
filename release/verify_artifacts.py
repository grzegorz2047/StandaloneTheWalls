#!/usr/bin/env python3
"""Verify immutable Sunderfront alpha release archives and checksums."""

from __future__ import annotations

import hashlib
import json
import pathlib
import struct
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
FIXED_ZIP_TIMESTAMP = (1980, 1, 1, 0, 0, 0)
PE_MACHINE_AMD64 = 0x8664


def digest(path: pathlib.Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def verify_text_entries(
    path: pathlib.Path,
    expected_fragments: dict[str, tuple[str, ...]],
) -> None:
    with zipfile.ZipFile(path) as archive:
        for name, fragments in expected_fragments.items():
            text = archive.read(name).decode("utf-8")
            for fragment in fragments:
                require(
                    fragment in text,
                    f"{path.name}: {name} is missing required fragment: {fragment}",
                )


def verify_zip(path: pathlib.Path, root: str, required: set[str], client: bool) -> None:
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        require(names, f"{path.name}: empty archive")
        require(len(names) == len(set(names)), f"{path.name}: duplicate entries")
        for name in names:
            normalized = name.replace("\\", "/")
            require(not normalized.startswith("/"), f"{path.name}: absolute entry")
            require(
                ".." not in pathlib.PurePosixPath(normalized).parts,
                f"{path.name}: traversal entry",
            )
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
            require(
                root + "/bin/sunderfront-direct-connect-smoke" not in names,
                f"{path.name}: smoke tool exposed in bin",
            )
            require(
                root + "/bin/sunderfront-direct-connect-smoke.bat" not in names,
                f"{path.name}: Windows smoke tool exposed in bin",
            )
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


def require_pe_x64(payload: bytes, label: str) -> None:
    require(len(payload) >= 64 and payload[:2] == b"MZ", f"{label}: invalid PE header")
    pe_offset = struct.unpack_from("<I", payload, 0x3C)[0]
    require(pe_offset + 6 <= len(payload), f"{label}: truncated PE header")
    require(payload[pe_offset : pe_offset + 4] == b"PE\0\0", f"{label}: missing PE signature")
    machine = struct.unpack_from("<H", payload, pe_offset + 4)[0]
    require(machine == PE_MACHINE_AMD64, f"{label}: executable is not x64")


def verify_windows_app_zip(path: pathlib.Path, root: str) -> None:
    required = {
        root + "/Sunderfront.exe",
        root + "/README.md",
        root + "/README-PL.txt",
        root + "/ICON-LICENSE.md",
        root + "/LICENSE.txt",
        root + "/assets/assets.lock.json",
        root + "/app/Sunderfront.cfg",
        root + "/runtime/release",
        root + "/runtime/bin/java.exe",
    }
    forbidden_tools = {
        "javac.exe",
        "javadoc.exe",
        "jpackage.exe",
        "jcmd.exe",
        "jconsole.exe",
    }
    with zipfile.ZipFile(path) as archive:
        infos = archive.infolist()
        names = [info.filename for info in infos]
        require(names, f"{path.name}: empty Windows app-image archive")
        require(names == sorted(names), f"{path.name}: entries are not sorted")
        require(len(names) == len(set(names)), f"{path.name}: duplicate entries")
        require(names[0] == root + "/", f"{path.name}: missing canonical root entry")

        for info in infos:
            normalized = info.filename.replace("\\", "/")
            pure = pathlib.PurePosixPath(normalized)
            require(not normalized.startswith("/"), f"{path.name}: absolute entry")
            require(".." not in pure.parts, f"{path.name}: traversal entry")
            require(
                normalized == root + "/" or normalized.startswith(root + "/"),
                f"{path.name}: unexpected archive root",
            )
            require(
                info.date_time == FIXED_ZIP_TIMESTAMP,
                f"{path.name}: non-deterministic timestamp: {normalized}",
            )
            relative_parts = tuple(part.lower() for part in pure.parts[1:])
            require(
                not {"data", "credentials", "cache"}.intersection(relative_parts),
                f"{path.name}: runtime data path included: {normalized}",
            )
            lowered = normalized.lower()
            require(
                not lowered.endswith(FORBIDDEN_SUFFIXES),
                f"{path.name}: runtime or credential file included: {normalized}",
            )
            if info.is_dir():
                continue
            require(info.file_size > 0, f"{path.name}: empty file: {normalized}")
            require(
                pure.name.lower() not in forbidden_tools,
                f"{path.name}: JDK tool included: {normalized}",
            )
            if pure.suffix.lower() not in {".jar", ".class"}:
                payload = archive.read(info)
                require(
                    not any(marker in payload for marker in PRIVATE_MARKERS),
                    f"{path.name}: private-key marker in {normalized}",
                )

        missing = sorted(required.difference(names))
        require(not missing, f"{path.name}: missing entries: {', '.join(missing)}")
        require(root + "/URUCHOM_KLIENTA.bat" not in names, f"{path.name}: legacy BAT exposed")
        require(
            any(name.startswith(root + "/app/") and name.endswith(".jar") for name in names),
            f"{path.name}: no application jars",
        )
        lock = json.loads(archive.read(root + "/assets/assets.lock.json").decode("utf-8"))
        require(lock == EMPTY_ASSET_LOCK, f"{path.name}: asset lock is not empty alpha lock")
        runtime_release = archive.read(root + "/runtime/release").decode("utf-8")
        require(
            'JAVA_VERSION="21.' in runtime_release or 'JAVA_VERSION="21"' in runtime_release,
            f"{path.name}: bundled runtime is not Java 21",
        )
        require_pe_x64(archive.read(root + "/Sunderfront.exe"), path.name + ": Sunderfront.exe")
        require_pe_x64(
            archive.read(root + "/runtime/bin/java.exe"),
            path.name + ": runtime java.exe",
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
    require_windows = False
    if len(sys.argv) == 4:
        require(sys.argv[3] == "--require-windows", "unknown verification option")
        require_windows = True
    elif len(sys.argv) != 3:
        print(
            "usage: verify_artifacts.py <release-dir> <version> [--require-windows]",
            file=sys.stderr,
        )
        return 2

    release_dir = pathlib.Path(sys.argv[1]).resolve()
    version = sys.argv[2]
    client_name = f"sunderfront-client-{version}.zip"
    server_name = f"sunderfront-server-{version}.zip"
    windows_name = f"sunderfront-client-windows-x64-{version}.zip"
    client_archive = release_dir / client_name
    server_archive = release_dir / server_name
    windows_archive = release_dir / windows_name
    require(client_archive.is_file(), f"missing {client_name}")
    require(server_archive.is_file(), f"missing {server_name}")
    if require_windows:
        require(windows_archive.is_file(), f"missing {windows_name}")

    client_root = f"sunderfront-client-{version}"
    server_root = f"sunderfront-server-{version}"
    verify_zip(
        client_archive,
        client_root,
        {
            client_root + "/README.md",
            client_root + "/README-PL.txt",
            client_root + "/URUCHOM_KLIENTA.bat",
            client_root + "/assets/assets.lock.json",
            client_root + "/bin/sunderfront-client",
            client_root + "/bin/sunderfront-client.bat",
            client_root + "/tools/sunderfront-direct-connect-smoke",
            client_root + "/tools/sunderfront-direct-connect-smoke.bat",
            client_root + "/tools/windows/require-java-21.bat",
        },
        True,
    )
    verify_zip(
        server_archive,
        server_root,
        {
            server_root + "/README.md",
            server_root + "/README-PL.txt",
            server_root + "/1_GENERUJ_CREDENTIALS.bat",
            server_root + "/2_URUCHOM_SERWER.bat",
            server_root + "/config/server.properties",
            server_root + "/config/identity.properties",
            server_root + "/config/tls.properties",
            server_root + "/data/README.md",
            server_root + "/credentials/README.md",
            server_root + "/bin/sunderfront-server",
            server_root + "/bin/sunderfront-server.bat",
            server_root + "/bin/sunderfront-server-credentials",
            server_root + "/bin/sunderfront-server-credentials.bat",
            server_root + "/tools/windows/require-java-21.bat",
        },
        False,
    )

    java_check_fragments = (
        "java.specification.version =",
        "sun.arch.data.model =",
        "os.arch =",
        'if not "%JAVA_VERSION%"=="21"',
        'if "%JAVA_DATA_MODEL%"=="64"',
    )
    verify_text_entries(
        client_archive,
        {
            client_root + "/URUCHOM_KLIENTA.bat": (
                'call "tools\\windows\\require-java-21.bat"',
                '--data-dir "%~dp0data"',
                "SUNDERFRONT_NO_PAUSE",
            ),
            client_root + "/tools/windows/require-java-21.bat": java_check_fragments,
        },
    )
    verify_text_entries(
        server_archive,
        {
            server_root + "/1_GENERUJ_CREDENTIALS.bat": (
                'call "tools\\windows\\require-java-21.bat"',
                '--output "%~dp0credentials"',
                "SUNDERFRONT_NO_PAUSE",
            ),
            server_root + "/2_URUCHOM_SERWER.bat": (
                "credentials\\server-ed25519-key.pk8",
                '--config "%~dp0config\\server.properties"',
                '--identity-config "%~dp0config\\identity.properties"',
                '--tls-config "%~dp0config\\tls.properties"',
                "SUNDERFRONT_NO_PAUSE",
            ),
            server_root + "/tools/windows/require-java-21.bat": java_check_fragments,
        },
    )

    expected_archives = [client_archive, server_archive]
    if windows_archive.is_file():
        windows_root = f"sunderfront-client-windows-x64-{version}"
        verify_windows_app_zip(windows_archive, windows_root)
        expected_archives.append(windows_archive)
    verify_checksums(release_dir, expected_archives)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, struct.error, zipfile.BadZipFile, json.JSONDecodeError) as failure:
        print(f"release verification failed: {failure}", file=sys.stderr)
        raise SystemExit(1) from None
