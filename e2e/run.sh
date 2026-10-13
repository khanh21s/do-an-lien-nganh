#!/usr/bin/env bash
# Runs the Phase 1 browser E2E in the pinned Playwright image against the local compose stack.
# Usage: [ADMIN_EMAIL=.. ADMIN_PASSWORD=.. DEMO_CODE=PROMO-DEMO1-MONTH] e2e/run.sh   (stack must be up)
# DEMO_CODE is consumed by the run; use another seeded code (or docker-compose down -v) for the next run.
# Note: the snap Docker daemon cannot see /tmp, so this project lives in the repo (outputs are git-ignored).
set -euo pipefail
cd "$(dirname "$0")"
IMG=mcr.microsoft.com/playwright:v1.47.2-jammy
pw() { docker run --rm --network host --ipc=host -u "$(id -u):$(id -g)" -e HOME=/tmp -e ADMIN_EMAIL -e ADMIN_PASSWORD -e DEMO_CODE -v "$PWD":/work -w /work "$IMG" "$@"; }
[ -d node_modules/@playwright/test ] || pw npm ci --no-audit --no-fund
rm -f state.json console-errors.json
rc=0
pw npx playwright test -g "0[1-5] " || rc=$?
# make the user's learned cards due now so /vocabulary/review has a queue (no UI for time travel)
uid=$(python3 -c 'import json;print(json.load(open("state.json"))["userAuth"]["userId"])')
docker exec parroto-postgres psql -q -U parroto -d vocabulary_db -c "UPDATE user_vocab SET next_review_at = now() - interval '1 hour' WHERE user_id = '$uid';"
pw npx playwright test -g "0(5b|[6-9]) " || rc=$?
exit $rc
