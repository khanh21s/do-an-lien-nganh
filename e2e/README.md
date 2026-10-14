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
