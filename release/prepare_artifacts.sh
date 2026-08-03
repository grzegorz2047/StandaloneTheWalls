#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT_DIR"

VERSION=$(tr -d '\r\n' < release/version.txt)
CLIENT_ARCHIVE="sunderfront-client-${VERSION}.zip"
SERVER_ARCHIVE="sunderfront-server-${VERSION}.zip"
RELEASE_DIR="$ROOT_DIR/build/release"

./gradlew --no-daemon :client:distZip :server:distZip

rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR"
cp "client/build/distributions/$CLIENT_ARCHIVE" "$RELEASE_DIR/$CLIENT_ARCHIVE"
cp "server/build/distributions/$SERVER_ARCHIVE" "$RELEASE_DIR/$SERVER_ARCHIVE"
(
  cd "$RELEASE_DIR"
  sha256sum "$CLIENT_ARCHIVE" "$SERVER_ARCHIVE" | LC_ALL=C sort > SHA256SUMS
)
python3 release/verify_artifacts.py "$RELEASE_DIR" "$VERSION"
