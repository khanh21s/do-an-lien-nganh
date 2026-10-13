#!/usr/bin/env bash
# API gateway verify (docs/architecture.md §1.1): install from lockfile, syntax check,
# load the auth middleware with a valid dummy secret - inside node:20-alpine.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp \
  -v "$ROOT/api-gateway":/app -w /app node:20-alpine sh -c '
    set -e
    if [ -f package-lock.json ]; then
      npm ci --no-audit --no-fund
    else
      echo "WARN: no package-lock.json yet (T02 adds it) - falling back to npm install --no-save"
      npm install --no-audit --no-fund --no-save --no-package-lock
    fi
    echo "== node --check server.js"; node --check server.js
    echo "== require middleware/auth"
    JWT_SECRET=verify_only_secret_at_least_32_characters_long node -e "require(\"./middleware/auth\")"
  '
echo "verify-gw: OK"
