#!/usr/bin/env bash
# Frontend verify (docs/architecture.md §1.1): npm ci + type-check + lint + unit tests,
# all inside node:20-alpine (no npm needed on the host).
# Lint / vitest run only once their config exists (.eslintrc.json / vitest in package.json),
# so the script also works on branches from before T06.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -e NEXT_TELEMETRY_DISABLED=1 \
  -v "$ROOT/frontend":/app -w /app node:20-alpine sh -c '
    set -e
    npm ci --no-audit --no-fund
    echo "== tsc --noEmit"; npx tsc --noEmit
    if [ -f .eslintrc.json ] || [ -f .eslintrc.js ] || [ -f .eslintrc ]; then
      echo "== next lint"; npx next lint
    else
      echo "== next lint: skipped (no .eslintrc yet)"
    fi
    if grep -q "\"vitest\"" package.json; then
      echo "== vitest run"; npx vitest run
    else
      echo "== vitest: skipped (not installed yet)"
    fi
  '
echo "verify-fe: OK"
