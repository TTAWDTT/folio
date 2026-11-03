#!/usr/bin/env bash
set -euo pipefail
repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$repo_root"
rm -f web/test-results/unit.xml web/test-results/e2e.xml
npm --prefix web ci
npm --prefix web run typecheck
npm --prefix web run test
npm --prefix web run test:e2e
./gradlew cleanTest test buildPlugin --console=plain
python3 scripts/verify-results.py
