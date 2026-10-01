#!/usr/bin/env bash
# Builds unsigned release artifacts; never signs with production keys or publishes.
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$NBACK_ROOT"
if [[ "${1:-}" == "--" ]]; then shift; fi
python3 scripts/release_preflight.py build -- "$@"
