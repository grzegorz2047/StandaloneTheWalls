#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT_DIR"
VERSION=$(tr -d '\r\n' < release/version.txt)
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT
cp -R build/release/. "$TEMP_DIR/"
printf 'corruption' >> "$TEMP_DIR/sunderfront-client-${VERSION}.zip"

if python3 release/verify_artifacts.py "$TEMP_DIR" "$VERSION"; then
  echo "tampered archive unexpectedly passed checksum verification" >&2
  exit 1
fi
