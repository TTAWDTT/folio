#!/usr/bin/env bash
set -euo pipefail
repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$repo_root"
./gradlew cleanTest test buildPlugin --console=plain
python3 scripts/verify-results.py
