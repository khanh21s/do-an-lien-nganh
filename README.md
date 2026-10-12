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

## Quick Start

```bash
cp .env.example .env
# edit .env: at minimum set a real JWT_SECRET; AWS/Azure/VNPay/mail keys are optional
# (features needing them fail gracefully at runtime until configured — see below)

docker-compose up -d
docker-compose ps                 # wait until everything reports "healthy"
curl http://localhost:8000/health
```

Open the app at **http://localhost:3000** and register a new account, or call the API
directly through the gateway:
```bash
curl -X POST http://localhost:8000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123","cefrLevel":"B1"}'
```

## Project Layout

```
├── docker-compose.yml       # all 9 containers: postgres, redis, 5 services, gateway, frontend
├── init-db.sql              # database-per-service schema, runs on first postgres start
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

- **JWT auth**, shared HMAC secret, validated independently by every service (no per-request call back to auth-service). Refresh token rotation, Redis logout blacklist (checked in auth-service only — see Known limitations).
- **Google / Apple Sign-In**: real token verification (Google tokeninfo endpoint; Apple JWKS + RS256 signature check) — needs `GOOGLE_CLIENT_ID` / `APPLE_CLIENT_ID` in `.env` to be strict about audience, works without them too.
- **Dictation**: word-by-word diff with Levenshtein-based typo tolerance, difficulty-weighted scoring.
- **Shadowing**: S3 presigned upload, Azure Pronunciation Assessment via plain REST call (not the native SDK — see Known limitations), red/yellow/green phoneme scoring.
- **Vocabulary SRS**: SM-2 algorithm implemented exactly per spec (interval 1 → 3 → interval×easeFactor, ease-factor formula, floor 1.3).
- **Subscriptions**: VNPay payment URL construction + HMAC-SHA512 callback signature verification, activation codes, Premium gating for Pro-only vocab decks (calls subscription-service internally).
- **Admin authorization**: `ADMIN_EMAILS` allow-list (env var) gates lesson/deck write endpoints — a deliberate MVP stand-in for a full roles table.
- Redis caching (lessons, decks, stats) with cache-aside invalidation; standardized error JSON (`error`, `status`, `timestamp`, `requestId`) across every service and the gateway.
- **Frontend (Next.js)**: full user-facing app — auth (email/password + optional Google Sign-In), lesson browsing, dictation practice with word-diff scoring display, shadowing practice with in-browser recording (`MediaRecorder`) and phoneme-level red/yellow/green feedback, vocabulary deck browsing, SRS flashcard review session, notebook, practice history, subscription management (VNPay redirect + activation codes + transaction history), profile settings — plus an Admin section (lesson/sentence/audio CRUD, deck/card CRUD) gated the same way the backend gates it (see limitation below).

## Known limitations / deliberate simplifications

1. **Logout blacklist is auth-service-only.** A token logged out on auth-service is rejected there, but other services don't re-check the blacklist (they only verify the signature/expiry). Acceptable for an MVP; add a blacklist check to each service's `JwtAuthFilter` if this needs to be airtight.
2. **No roles table.** Admin endpoints are gated by an `ADMIN_EMAILS` env var comparison, not a real permissions system.
3. **External integrations (Google/Apple OAuth, AWS S3, Azure Speech, VNPay, SMTP) need real credentials in `.env`.** Without them, those specific calls fail at runtime with a clear error (503/502) — everything else keeps working.
4. **`practice_attempt.sentence_id`** was added to `init-db.sql` (the original schema draft omitted it, but the API needs it to score a specific sentence).
5. Ports 8001-8005 are published to the host for direct testing/debugging (matching the docs' curl examples) as well as being reachable through the gateway on 8000 — don't expose this compose file directly to the internet as-is.
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
