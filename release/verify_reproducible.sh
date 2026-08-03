#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT_DIR"
VERSION=$(tr -d '\r\n' < release/version.txt)
CLIENT_ARCHIVE="sunderfront-client-${VERSION}.zip"
SERVER_ARCHIVE="sunderfront-server-${VERSION}.zip"
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT

./gradlew --no-daemon clean :client:distZip :server:distZip
cp "client/build/distributions/$CLIENT_ARCHIVE" "$TEMP_DIR/client-first.zip"
cp "server/build/distributions/$SERVER_ARCHIVE" "$TEMP_DIR/server-first.zip"

./gradlew --no-daemon clean :client:distZip :server:distZip
cmp "$TEMP_DIR/client-first.zip" "client/build/distributions/$CLIENT_ARCHIVE"
cmp "$TEMP_DIR/server-first.zip" "server/build/distributions/$SERVER_ARCHIVE"
