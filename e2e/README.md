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
