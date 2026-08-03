#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT_DIR"
VERSION=$(tr -d '\r\n' < release/version.txt)
TEMP_DIR=$(mktemp -d)
SERVER_PID=""
cleanup() {
  if [[ -n "$SERVER_PID" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true
  fi
  rm -rf "$TEMP_DIR"
}
trap cleanup EXIT

unzip -q "build/release/sunderfront-server-${VERSION}.zip" -d "$TEMP_DIR"
unzip -q "build/release/sunderfront-client-${VERSION}.zip" -d "$TEMP_DIR"
SERVER_DIR="$TEMP_DIR/sunderfront-server-${VERSION}"
CLIENT_DIR="$TEMP_DIR/sunderfront-client-${VERSION}"

read -r RELIABLE_PORT REALTIME_PORT < <(
  python3 - <<'PY'
import socket
ports = []
for _ in range(2):
    sock = socket.socket()
    sock.bind(("127.0.0.1", 0))
    ports.append(sock.getsockname()[1])
    sock.close()
print(*ports)
PY
)

python3 - "$SERVER_DIR/config/server.properties" "$RELIABLE_PORT" "$REALTIME_PORT" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
text = text.replace("server.tick-rate=20", "server.tick-rate=60")
text = text.replace("server.reliable-port=27420", f"server.reliable-port={sys.argv[2]}")
text = text.replace("server.realtime-port=27421", f"server.realtime-port={sys.argv[3]}")
path.write_text(text, encoding="utf-8")
PY

mkdir -p "$SERVER_DIR/data" "$CLIENT_DIR/data"
"$SERVER_DIR/bin/sunderfront-server-credentials" --output "$SERVER_DIR/credentials"
FINGERPRINT=$(tr -d '\r\n' < "$SERVER_DIR/credentials/server-fingerprint.txt")

"$SERVER_DIR/bin/sunderfront-server" \
  --config "$SERVER_DIR/config/server.properties" \
  --identity-config "$SERVER_DIR/config/identity.properties" \
  --tls-config "$SERVER_DIR/config/tls.properties" \
  --validate-config

"$SERVER_DIR/bin/sunderfront-server" \
  --config "$SERVER_DIR/config/server.properties" \
  --identity-config "$SERVER_DIR/config/identity.properties" \
  --tls-config "$SERVER_DIR/config/tls.properties" \
  --run-for-ticks 1200 \
  >"$TEMP_DIR/server.log" 2>&1 &
SERVER_PID=$!

python3 - "$RELIABLE_PORT" <<'PY'
import socket
import sys
import time
port = int(sys.argv[1])
deadline = time.monotonic() + 15
last = None
while time.monotonic() < deadline:
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=0.25):
            raise SystemExit(0)
    except OSError as failure:
        last = failure
        time.sleep(0.05)
raise SystemExit(f"server listener did not open: {last}")
PY

"$CLIENT_DIR/bin/sunderfront-client" --smoke --data-dir "$CLIENT_DIR/data"
"$CLIENT_DIR/bin/sunderfront-direct-connect-smoke" \
  --endpoint "127.0.0.1:${RELIABLE_PORT}" \
  --handle release_smoke \
  --expected-fingerprint "$FINGERPRINT" \
  --data-dir "$CLIENT_DIR/data" \
  --require-first-use

wait "$SERVER_PID"
SERVER_PID=""
