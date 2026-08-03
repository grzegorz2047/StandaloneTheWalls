#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT_DIR"

bash release/verify_reproducible.sh
bash release/prepare_artifacts.sh
bash release/verify_checksum_failure.sh
bash release/e2e_from_distributions.sh
