# Parroto

English-learning platform — Dictation, Shadowing, Vocabulary SRS (SM-2), and Premium subscriptions. Microservices backend (5 independent Spring Boot services, database-per-service) behind a Node/Express API Gateway, with a Next.js web frontend.

```
Frontend (Next.js, 3000)
    -> API Gateway (Node/Express, 8000)
        -> auth-service         (Spring Boot, 8001) - auth_db
        -> content-service      (Spring Boot, 8002) - content_db
        -> practice-service     (Spring Boot, 8003) - practice_db
        -> vocabulary-service   (Spring Boot, 8004) - vocabulary_db
        -> subscription-service (Spring Boot, 8005) - subscription_db
PostgreSQL 16 + Redis 7 (shared instances, one database/keyspace per service)
```

The frontend calls the gateway directly from the browser (no server-side rendering of
authenticated data) — see `frontend/README` notes below for why.

## Quick Start (demo mode, no external keys)

```bash
cp .env.example .env
# REQUIRED: replace the two dev-only samples with your own random values
#   JWT_SECRET=$(openssl rand -base64 48)      (>= 32 chars, shared by gateway + 5 services)
#   INTERNAL_TOKEN=$(openssl rand -hex 32)     (>= 32 chars, service-to-service /internal/**)
# Everything else works as-is: AWS / Azure / Google / VNPay / mail keys are optional.

docker-compose up -d --build
docker-compose ps                 # wait until everything reports "healthy"
curl http://localhost:8000/health
```

`docker-compose` refuses to start with a clear message when `JWT_SECRET` or
`INTERNAL_TOKEN` is missing (`required variable JWT_SECRET is missing a value`).

Open the app at **http://localhost:3000** and register a new account, or call the API
directly through the gateway:
```bash
curl -X POST http://localhost:8000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123","cefrLevel":"B1"}'
```

Only ports **3000** (frontend) and **8000** (gateway) are published to the host. For
direct access to Postgres/Redis/the services while developing:
```bash
cp docker-compose.override.yml.example docker-compose.override.yml   # git-ignored
docker-compose up -d    # now also 127.0.0.1:5432, 6379, 8001-8005
```

### Mock mode: S3 and Azure are optional

| Env (`.env`) | Default | Behaviour |
|---|---|---|
| `STORAGE_PROVIDER` | `auto` | `auto` → S3 when `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` are set (and don't start with `your_`), otherwise **local files** under `MEDIA_ROOT` (docker volume `media_data`). Force with `s3` / `local`. |
| `SPEECH_PROVIDER` | `auto` | `auto` → Azure when `AZURE_SPEECH_KEY` is set, otherwise a **deterministic mock scorer** (no network; the UI shows "Chế độ demo – điểm mô phỏng"). Force with `azure` / `mock`. |
| `MEDIA_ROOT` | `/data/media` | Local storage root inside content-service / practice-service. |

Forcing `s3`/`azure` without keys makes the service fail at startup with an explicit log.
Seed lessons always play from local files (`local:seed/...`), even when S3 is enabled.

### Demo data

- **5 lessons × 5 sentences** with real audio (TTS) in `media/seed/lesson-<n>/` — `full.mp3`
  for the lesson, `s<order>.mp3` per sentence. Regenerate after editing the seeded
  sentences in `init-db.sql`: `scripts/gen-seed-audio.sh` (Docker: espeak-ng + ffmpeg).
- **3 vocabulary decks** (the third is Pro-only).
- **Plans**: 1 tháng 79.000đ, 3 tháng 169.000đ, 1 năm 499.000đ (lifetime plan: Phase 2).
- **Activation codes** (single use each, valid until 2027-12-31) — enter them on the
  subscription page or `POST /subscriptions/activate-code {"code": "..."}`:

  | Code | Plan |
  |---|---|
  | `PROMO-DEMO1-MONTH` | 1 tháng |
  | `PROMO-DEMO3-MONTH` | 3 tháng |
  | `PROMO-DEMOY-EARLY` | 1 năm |

  A used code stays used; to reset all demo data run `docker-compose down -v && docker-compose up -d`.

### Admin account (demo)

Admin rights come from the `ADMIN_EMAILS` env var (comma-separated, read by every
service; default `admin@parroto.com`). There is no seeded user — just **register**
`admin@parroto.com` (any password) at http://localhost:3000/register; the Admin menu
(lessons, vocabulary, activation codes) appears after login. To make other people admins:
```bash
# .env
ADMIN_EMAILS=admin@parroto.com,teacher@example.com
docker-compose up -d     # recreates the services with the new value
```

### Database passwords

The per-service roles (`auth_user`, `content_user`, ...) are created by
`initdb/00-roles.sh` from the `*_DB_PASSWORD` variables in `.env` on the **first** start
of an empty Postgres volume (`init-db.sql` then creates schema + seed). The same
variables are passed to each service, so changing a password means:
`docker-compose down -v` (wipes data) and `up` again — or `ALTER ROLE ... PASSWORD` by hand.

## Operations notes

**Apply DB migrations to an existing volume** — `init-db.sql` only runs on the first start
of an empty Postgres volume. Later schema changes ship as idempotent scripts in
`scripts/migrations/` (safe to re-run); apply them in order, e.g. T13 (personal words in the
notebook, `vocab_deck.owner_id`), then restart the service (`ddl-auto: validate` needs the column):
```bash
docker exec -i parroto-postgres psql -U parroto -d vocabulary_db -v ON_ERROR_STOP=1 < scripts/migrations/001_personal_vocab.sql
docker-compose restart vocabulary-service
```

**Normalise old practice scores** — since Phase 1 a score is always 0–100
(`accuracy × difficulty weight`). Rows written by older versions may be above 100; fix
them once, manually:
```bash
docker-compose exec postgres psql -U parroto -d practice_db \
  -c "UPDATE practice_attempt SET score = LEAST(score, 100) WHERE score > 100;"
```

**Test the VNPay IPN locally** — VNPay cannot call `localhost`, so simulate the signed
server-to-server request (HMAC-SHA512 with `VNPAY_SECRET`, same algorithm as the service):
```bash
# 1. log in, then create a payment -> note "transactionId" and "amount"
curl -s -X POST http://localhost:8000/subscriptions/create-payment \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"planId":"plan-1month"}'
# 2. send the signed IPN
scripts/vnpay-ipn-test.sh <transactionId> 79000          # -> {"RspCode":"00",...}, Premium active
scripts/vnpay-ipn-test.sh <transactionId> 79000          # again -> RspCode 02 (already processed)
scripts/vnpay-ipn-test.sh <otherTransactionId> 1000      # wrong amount -> RspCode 04, transaction failed
BAD_SIGNATURE=1 scripts/vnpay-ipn-test.sh <transactionId> 79000   # -> RspCode 97
```
`VNPAY_SECRET` / `VNPAY_MERCHANT_ID` must be non-empty in `.env` (the sample placeholders
are enough for this local test). The browser return URL
(`/subscriptions/payment-callback`) only displays/redirects.

**Redis is fail-open for the JWT blacklist.** Logout writes `token_blacklist:{jti}` to
Redis; the gateway and services reject blacklisted tokens. If Redis is unreachable, the
check is skipped (WARN in the logs) and still-valid tokens keep working until they expire
— a deliberate availability trade-off for the demo. Java services use a 2 s Redis
timeout (`REDIS_TIMEOUT`) so a hung Redis does not stall requests.

## Verify & CI

No local Node/Maven needed — everything runs in Docker:
```bash
scripts/verify-fe.sh                       # frontend: npm ci, tsc --noEmit, next lint, vitest
scripts/verify-gw.sh                       # gateway: npm ci, node --check, load auth middleware
scripts/verify-java.sh content-service     # one service: mvn test (JDK 21); no argument = all 5
docker-compose config -q                   # compose + .env valid
```
GitHub Actions (`.github/workflows/ci.yml`) runs the same checks: `frontend`, `gateway`,
`java` (matrix over the 5 services, Temurin 21) and `compose`.

## Project Layout

```
├── docker-compose.yml       # all 9 containers: postgres, redis, 5 services, gateway, frontend
├── docker-compose.override.yml.example  # dev-only: publish 5432/6379/8001-8005 on 127.0.0.1
├── initdb/00-roles.sh        # creates DB roles/databases with passwords from env (first start)
├── init-db.sql              # database-per-service schema + seed, runs on first postgres start
├── media/seed/               # generated seed lesson audio (scripts/gen-seed-audio.sh)
├── scripts/                  # verify-{fe,gw,java}.sh, gen-seed-audio.sh, vnpay-ipn-test.sh
├── .github/workflows/ci.yml  # CI: frontend, gateway, java x5, compose
├── .env.example              # environment variable template
├── api-gateway/              # Node/Express - routing, JWT check, rate limiting
│   ├── server.js
│   └── middleware/{auth,errorHandler,logger,rateLimiter}.js
├── services/
│   ├── auth-service/         # register/login/OAuth/JWT issuing, profile
│   ├── content-service/      # lessons, sentences, audio streaming (S3)
│   ├── practice-service/     # dictation scoring, shadowing (Azure), stats/streaks
│   ├── vocabulary-service/   # decks, cards, SM-2 spaced repetition
│   └── subscription-service/ # plans, VNPay payments, activation codes
├── frontend/                 # Next.js 14 (App Router) + TypeScript + Tailwind
│   ├── src/app/               # pages: public (landing/lessons/pricing) + (app) authed shell + admin
│   ├── src/components/        # ui/, layout/, audio/, practice/ primitives
│   ├── src/lib/                # api.ts (axios+interceptors), endpoints/*.ts, types.ts (mirrors backend DTOs)
│   └── src/store/               # Zustand auth store (persisted to localStorage)
└── files/                    # original planning docs (24-prompt spec, reference configs) - archived, not used at runtime
```

Each Java service follows the same internal layout: `config/`, `security/`, `entity/`, `repository/`, `dto/`, `service/`, `controller/`, `exception/`.

## What's implemented

- **JWT auth**, shared HMAC secret, validated independently by every service (no per-request call back to auth-service). Refresh token rotation, Redis logout blacklist (fail-open — see Known limitations).
- **Google / Apple Sign-In**: real token verification (Google tokeninfo endpoint; Apple JWKS + RS256 signature check) — needs `GOOGLE_CLIENT_ID` / `APPLE_CLIENT_ID` in `.env` to be strict about audience, works without them too.
- **Dictation**: word-by-word diff with Levenshtein-based typo tolerance, difficulty-weighted scoring.
- **Shadowing**: S3 presigned upload, Azure Pronunciation Assessment via plain REST call (not the native SDK — see Known limitations), red/yellow/green phoneme scoring.
- **Vocabulary SRS**: standard SM-2 (interval 1 → 6 → interval×easeFactor, ease-factor formula, floor 1.3; see docs/architecture.md §4.6).
- **Subscriptions**: VNPay payment URL construction + HMAC-SHA512 callback signature verification, activation codes, Premium gating for Pro-only vocab decks (calls subscription-service internally).
- **Admin authorization**: `ADMIN_EMAILS` allow-list (env var) gates lesson/deck write endpoints — a deliberate MVP stand-in for a full roles table.
- Redis caching (lessons, decks, stats) with cache-aside invalidation; standardized error JSON (`error`, `status`, `timestamp`, `requestId`) across every service and the gateway.
- **Frontend (Next.js)**: full user-facing app — auth (email/password + optional Google Sign-In), lesson browsing, dictation practice with word-diff scoring display, shadowing practice with in-browser recording (`MediaRecorder`) and phoneme-level red/yellow/green feedback, vocabulary deck browsing, SRS flashcard review session, notebook, practice history, subscription management (VNPay redirect + activation codes + transaction history), profile settings — plus an Admin section (lesson/sentence/audio CRUD, deck/card CRUD) gated the same way the backend gates it (see limitation below).

## Known limitations / deliberate simplifications

1. **JWT blacklist is fail-open.** Logged-out tokens are rejected while Redis is up; if Redis is down the blacklist check is skipped (see Operations notes).
2. **No roles table.** Admin endpoints are gated by an `ADMIN_EMAILS` env var comparison, not a real permissions system.
3. **External integrations are optional.** Without AWS/Azure keys, storage and pronunciation scoring run in mock mode (see above). Google/Apple sign-in answer 503 until `GOOGLE_CLIENT_ID` / `APPLE_CLIENT_ID` are set; VNPay and SMTP need real credentials for real payments/emails.
4. **`practice_attempt.sentence_id`** was added to `init-db.sql` (the original schema draft omitted it, but the API needs it to score a specific sentence).
5. Only ports 3000 and 8000 are published; Postgres, Redis and 8001-8005 stay inside the docker network unless you opt in with `docker-compose.override.yml` (bound to 127.0.0.1). The compose file is still a demo setup — put TLS and real secrets in front of it before exposing it to the internet.
6. **The frontend fetches client-side only** (no server-side rendering of authenticated data, no Next.js middleware session). It stores the JWT in a Zustand store persisted to `localStorage` and attaches it via an Axios interceptor; protected routes redirect to `/login` client-side if no token is present after the store hydrates. This matches how the gateway actually authenticates (stateless bearer JWT, not cookies).
7. **Frontend admin-nav visibility is advisory, not a security boundary.** `GET /auth/profile` now returns an `isAdmin` flag (auth-service compares the caller's email against its own `ADMIN_EMAILS`, mirroring the allow-list every other service already enforces independently) and the sidebar only renders the Admin section when it's true — but the actual write endpoints are still, and only, gated by each service's own server-side check. Hitting an admin URL directly while not on the allow-list still gets the backend's real 403, surfaced as an inline error.
8. `VNPAY_RETURN_URL` **must** point at the gateway (`http://localhost:8000/subscriptions/payment-callback`), not directly at a frontend page — the original planning docs had this pointing at `localhost:3000/subscription/callback`, which would have skipped VNPay signature verification entirely and never activated any subscription. Fixed in `.env.example`/`docker-compose.yml`/`application.yml`; `subscription-service` then 302-redirects the browser to `FRONTEND_URL` + `/subscription/success` or `/subscription/failed`.

## Development

```bash
docker-compose logs -f <service-name>       # e.g. auth-service, api-gateway, frontend
docker-compose up -d --build <service-name> # rebuild+restart one service after a code change
docker-compose exec postgres psql -U parroto -d parroto   # note: individual service DBs are auth_db, content_db, etc.
docker-compose exec redis redis-cli
docker-compose down                          # stop (add -v to also wipe the postgres/redis volumes)
```

Frontend hot-reload dev loop (faster than rebuilding the Docker image on every change):
```bash
cd frontend
cp .env.example .env.local        # NEXT_PUBLIC_API_URL=http://localhost:8000
npm install
npm run dev                        # serves on :3000 with hot reload, talks to the gateway
                                    # started by `docker-compose up` on :8000
```
`NEXT_PUBLIC_*` vars are inlined into the browser bundle at **build** time — when changing
`NEXT_PUBLIC_API_URL` or `GOOGLE_CLIENT_ID` for the Dockerized frontend, rebuild its image
(`docker-compose up -d --build frontend`), a restart alone will not pick up the new value.
