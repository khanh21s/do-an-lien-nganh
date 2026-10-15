# Parroto browser E2E (Phase 1)

Playwright suite used by the final review (`docs/reports/review-final.md`). Runs in the pinned
`mcr.microsoft.com/playwright:v1.47.2-jammy` image against the running compose stack
(FE `:3000`, gateway `:8000`), Chromium with a fake microphone.

```bash
docker-compose up -d --build
ADMIN_PASSWORD='<ADMIN_PASSWORD from .env>' DEMO_CODE=PROMO-DEMO1-MONTH e2e/run.sh
```

- The suite registers a new user each run; `admin@parroto.com` (in `ADMIN_EMAILS`) is registered if missing.
- `DEMO_CODE` is consumed (one of the seeded `PROMO-DEMO1-MONTH`, `PROMO-DEMO3-MONTH`, `PROMO-DEMOY-EARLY`); re-seed with `docker-compose down -v`.
- Between the two phases `run.sh` makes the new user's cards due via `psql` so the review queue is not empty.
- Outputs (git-ignored): `screenshots/`, `test-results/`, `console-errors.json`.
- The project lives in the repo (not `/tmp`) because the snap Docker daemon cannot bind-mount `/tmp`.

`tests/admin-console.spec.ts` logs in through the UI as the seeded admin, so pass the admin credentials from `.env`:
```bash
set -a; . ./.env; set +a
docker run --rm --network host --ipc=host -u "$(id -u):$(id -g)" -e HOME=/tmp -e ADMIN_EMAIL -e ADMIN_PASSWORD \
  -v "$PWD/e2e":/work -w /work mcr.microsoft.com/playwright:v1.47.2-jammy npx playwright test admin-console
```

`tests/translate-game.spec.ts` (T15 "Dịch nhanh") needs no credentials; the second test waits for a full 60-second round:
```bash
docker run --rm --network host --ipc=host -u "$(id -u):$(id -g)" -e HOME=/tmp \
  -v "$PWD/e2e":/work -w /work mcr.microsoft.com/playwright:v1.47.2-jammy npx playwright test translate-game
```

`tests/game-phase2.spec.ts` (T16) needs a Premium account for the Survival test (skipped otherwise). Create one, then run:
```bash
PEMAIL="e2e_premium_$(date +%s)@test.local"
UID_=$(curl -s -X POST localhost:8000/auth/register -H 'Content-Type: application/json' \
  -d "{\"email\":\"$PEMAIL\",\"password\":\"Passw0rd!e2e\",\"cefrLevel\":\"B2\"}" | python3 -c 'import json,sys;print(json.load(sys.stdin)["userId"])')
docker exec parroto-postgres psql -U parroto -d subscription_db -c "INSERT INTO subscription (user_id, plan_id, start_at, end_at, status, payment_method) VALUES ('$UID_','plan-1month',now(),now()+interval '30 days','active','activation_code');"
docker run --rm --network host --ipc=host -u "$(id -u):$(id -g)" -e HOME=/tmp -e PREMIUM_EMAIL="$PEMAIL" -e PREMIUM_PASSWORD='Passw0rd!e2e' \
  -v "$PWD/e2e":/work -w /work mcr.microsoft.com/playwright:v1.47.2-jammy npx playwright test game-phase2
```

`tests/anki-session.spec.ts` (P2-anki) checks the Anki-style deck session on a fresh personal deck:
counters, the server's button labels (`<1 phút / 6 phút / 10 phút / 4 ngày`), Good → 10-minute step,
Easy → 4 days, learn-ahead of the last learning card, "Đã xong hôm nay". Like every spec it reads
`BASE_URL` (frontend, default `http://localhost:3000`) and `API_URL` (gateway, default
`http://localhost:8000`), so it can run against a second stack on other ports:
```bash
docker run --rm --network host --ipc=host -u "$(id -u):$(id -g)" -e HOME=/tmp \
  -e BASE_URL=http://localhost:13000 -e API_URL=http://localhost:18000 \
  -v "$PWD/e2e":/work -w /work mcr.microsoft.com/playwright:v1.47.2-jammy \
  npx playwright test anki-session deck-typing-retry t13-notebook-add custom-shortcuts
```
