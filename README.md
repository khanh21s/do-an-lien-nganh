# Parroto

Nền tảng học tiếng Anh với các tính năng nghe chép chính tả, luyện nói đuổi (shadowing), ôn từ vựng theo lịch SRS (SM-2) và gói Premium. Backend dùng kiến trúc microservice gồm 5 dịch vụ Spring Boot độc lập, mỗi dịch vụ có cơ sở dữ liệu riêng, đứng sau API Gateway Node/Express; frontend được xây dựng bằng Next.js.

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

Frontend gọi trực tiếp gateway từ trình duyệt (không kết xuất phía máy chủ cho dữ liệu đã xác thực). Xem ghi chú trong `frontend/README` để biết lý do.

## Bắt đầu nhanh (chế độ demo, không cần khóa dịch vụ bên ngoài)

```bash
cp .env.example .env
# BẮT BUỘC: thay hai giá trị mẫu chỉ dành cho phát triển bằng chuỗi ngẫu nhiên của bạn
#   JWT_SECRET=$(openssl rand -base64 48)      (>= 32 chars, shared by gateway + 5 services)
#   INTERNAL_TOKEN=$(openssl rand -hex 32)     (>= 32 chars, service-to-service /internal/**)
# Các mục còn lại dùng được ngay; khóa AWS / Azure / Google / VNPay / email đều không bắt buộc.

docker-compose up -d --build
docker-compose ps                 # wait until everything reports "healthy"
curl http://localhost:8000/health
```

Nếu thiếu `JWT_SECRET` hoặc `INTERNAL_TOKEN`, `docker-compose` sẽ từ chối khởi động và hiển thị thông báo rõ ràng (ví dụ: `required variable JWT_SECRET is missing a value`).

Mở ứng dụng tại **http://localhost:3000** để đăng ký tài khoản mới, hoặc gọi API trực tiếp qua gateway:
```bash
curl -X POST http://localhost:8000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123","cefrLevel":"B1"}'
```

Chỉ cổng **3000** (frontend) và **8000** (gateway) được mở ra máy chủ. Để truy cập trực tiếp Postgres/Redis/các dịch vụ khi phát triển:
```bash
cp docker-compose.override.yml.example docker-compose.override.yml   # git-ignored
docker-compose up -d    # now also 127.0.0.1:5432, 6379, 8001-8005
```

### Chế độ mô phỏng: S3 và Azure không bắt buộc

| Env (`.env`) | Default | Behaviour |
|---|---|---|
| `STORAGE_PROVIDER` | `auto` | `auto` sẽ dùng S3 nếu đã đặt `AWS_ACCESS_KEY_ID` và `AWS_SECRET_ACCESS_KEY` (và giá trị không bắt đầu bằng `your_`); nếu không sẽ dùng **tệp cục bộ** trong `MEDIA_ROOT` (Docker volume `media_data`). Có thể chọn rõ `s3` / `local`. |
| `SPEECH_PROVIDER` | `auto` | `auto` sẽ dùng Azure nếu đã đặt `AZURE_SPEECH_KEY`; nếu không sẽ dùng **bộ chấm điểm mô phỏng xác định** (không kết nối mạng; giao diện hiển thị "Chế độ demo – điểm mô phỏng"). Có thể chọn rõ `azure` / `mock`. |
| `MEDIA_ROOT` | `/data/media` | Thư mục gốc lưu trữ cục bộ bên trong content-service / practice-service. |

Chọn `s3`/`azure` mà không cung cấp khóa sẽ khiến dịch vụ dừng khi khởi động và ghi thông báo cụ thể vào log. Bài học mẫu luôn phát từ tệp cục bộ (`local:seed/...`), kể cả khi đã bật S3.

### Dữ liệu demo

- **5 bài học × 5 câu** có âm thanh TTS trong `media/seed/lesson-<n>/` — `full.mp3`
  cho toàn bài và `s<order>.mp3` cho từng câu. Tạo lại âm thanh sau khi sửa các câu mẫu trong
  `init-db.sql` bằng `scripts/gen-seed-audio.sh` (Docker dùng espeak-ng + ffmpeg).
- **3 bộ từ vựng** (bộ thứ ba chỉ dành cho Pro).
- **Gói dịch vụ**: 1 tháng 79.000đ, 3 tháng 169.000đ, 1 năm 499.000đ (gói trọn đời thuộc Phase 2).
- **Mã kích hoạt** (mỗi mã dùng một lần, có hiệu lực đến 2027-12-31) — nhập tại trang đăng ký hoặc gọi `POST /subscriptions/activate-code {"code": "..."}`:

  | Code | Plan |
  |---|---|
  | `PROMO-DEMO1-MONTH` | 1 tháng |
  | `PROMO-DEMO3-MONTH` | 3 tháng |
  | `PROMO-DEMOY-EARLY` | 1 năm |

  Mã đã dùng sẽ không thể dùng lại. Để tạo lại toàn bộ dữ liệu demo, chạy `docker-compose down -v && docker-compose up -d`.

### Tài khoản quản trị (demo)

Quyền quản trị được cấp qua biến môi trường `ADMIN_EMAILS` (các email phân cách bằng dấu phẩy; mọi dịch vụ đều đọc biến này; mặc định là `admin@parroto.com`). Không có sẵn tài khoản người dùng — hãy **đăng ký** `admin@parroto.com` (mật khẩu tùy chọn) tại http://localhost:3000/register. Sau khi đăng nhập, menu Quản trị (bài học, từ vựng, mã kích hoạt) sẽ xuất hiện. Để cấp quyền cho người khác:
```bash
# .env
ADMIN_EMAILS=admin@parroto.com,teacher@example.com
docker-compose up -d     # recreates the services with the new value
```

### Mật khẩu cơ sở dữ liệu

Các role riêng cho từng dịch vụ (`auth_user`, `content_user`, ...) được `initdb/00-roles.sh` tạo từ biến `*_DB_PASSWORD` trong `.env` khi Postgres khởi động **lần đầu** với volume trống (sau đó `init-db.sql` tạo schema và dữ liệu mẫu). Các biến này cũng được truyền cho từng dịch vụ. Vì vậy, muốn đổi mật khẩu, hãy chạy `docker-compose down -v` (xóa dữ liệu) rồi khởi động lại, hoặc tự chạy `ALTER ROLE ... PASSWORD`.

## Ghi chú vận hành

**Schema changes = Flyway migrations (since Phase 2).** Every Java service runs Flyway on
startup (before Hibernate's `ddl-auto: validate`) from its own
`services/<svc>/src/main/resources/db/migration/`:
- `V1__baseline.sql` is the frozen reference of the schema as it was after `init-db.sql` +
  `initdb/` seeds + `scripts/migrations/001`–`007` (pg_dump `--schema-only`). Because of
  `spring.flyway.baseline-on-migrate: true` / `baseline-version: 1`, it is **never executed** on
  an existing volume nor on a fresh one (where `init-db.sql` already created the tables): the
  first start only writes a `<< Flyway Baseline >>` row to `flyway_schema_history`. It would
  only run on a completely empty database. Never edit V1 (or any applied migration).
- **Every new schema change** is a new file `V<n>__<snake_case_description>.sql` in the owning
  service, starting at `V2`, applied automatically by `docker-compose up -d --build <svc>`. No
  manual `psql`, no edits to `init-db.sql`. Write plain forward-only SQL (Flyway runs each file
  once, in a transaction); keep seed data out of schema migrations unless the feature needs it.
- **Reserved version numbers** (upcoming feature work — do not reuse):

  | Service | Version | Feature |
  |---|---|---|
  | content-service | `V2` | premium lessons |
  | practice-service | `V2` | sentence review SRS |
  | vocabulary-service | `V2` | gamification |
  | vocabulary-service | `V3` | vocabulary groups / import / dictionary |
  | vocabulary-service | `V4` | Anki scheduling (P2-anki) |

  The next free numbers are content `V3`, practice `V3`, vocabulary `V5`, auth `V2`,
  subscription `V2`. Two branches must not ship the same version in one service — rebase and
  renumber before merging.
- `init-db.sql` and `scripts/migrations/` are **frozen history**: they still build a fresh
  volume (schema + seed data) and document how pre-Phase-2 volumes were upgraded, but they no
  longer receive changes. A fresh volume = `init-db.sql` (baseline) + Flyway `V2…` on the
  first service start.

**Legacy: pre-Flyway migrations on an old volume** — `init-db.sql` only runs on the first start
of an empty Postgres volume. A volume created before 001–007 existed needs those idempotent
scripts from `scripts/migrations/` (safe to re-run) applied in order BEFORE the first Flyway
start, e.g. T13 (personal words in the notebook, `vocab_deck.owner_id`), then restart the
service (`ddl-auto: validate` needs the column):
```bash
docker exec -i parroto-postgres psql -U parroto -d vocabulary_db -v ON_ERROR_STOP=1 < scripts/migrations/001_personal_vocab.sql
docker-compose restart vocabulary-service
```
T14 (admin plan management: `plan.is_active`, `plan.updated_at`) — apply before starting the
new subscription-service:
```bash
docker exec -i parroto-postgres psql -U parroto -d subscription_db -v ON_ERROR_STOP=1 < scripts/migrations/003_plan_active.sql
docker-compose restart subscription-service
```

**B2 vocabulary deck** — the free public deck "Từ vựng B2 thiết yếu" (734 cards: word, part of
speech, Vietnamese meaning, IPA, example + translation) comes from `data/vocab/b2-essential.tsv`.
Fresh volumes load it automatically (`initdb/02-seed-b2-vocab.sh`). For an existing DB, or after
editing the TSV:
```bash
python3 scripts/gen-b2-seed.py     # validate the TSV + regenerate scripts/migrations/002_seed_b2_deck.sql
docker exec -i parroto-postgres psql -U parroto -d vocabulary_db -v ON_ERROR_STOP=1 < scripts/migrations/002_seed_b2_deck.sql
docker exec parroto-redis sh -c "redis-cli --scan --pattern 'vocab:decks:*' | xargs -r redis-cli del"   # refresh the deck list cache
```
The script is idempotent (fixed deck id, skips words already in the deck), so it only adds new rows.

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
scripts/verify-java.sh content-service     # one service (+ services/common): mvn test (JDK 21); no argument = common + all 5
docker-compose config -q                   # compose + .env valid
```
GitHub Actions (`.github/workflows/ci.yml`) runs the same checks: `frontend`, `gateway`,
`java` (matrix over the 5 services, Temurin 21, each built with `services/common` via
`mvn -pl <svc> -am test` from `services/`) and `compose`.

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
│   ├── pom.xml               # Maven aggregator (common + 5 services); image build context
│   ├── common/               # parroto-common: shared JWT/admin/internal-token/error/cache classes
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

Each Java service follows the same internal layout: `config/`, `security/`, `entity/`, `repository/`, `dto/`, `service/`, `controller/`, `exception/`, plus `src/main/resources/db/migration/` (Flyway).
Shared infrastructure lives once in `services/common` (package `com.parroto.common.*`: `security.JwtValidator/JwtAuthFilter/AuthenticatedUser/AdminGuard/InternalTokenFilter`, `web.RequestIdFilter/RestAuthEntryPoint`, `exception.ApiException`, `dto.ErrorResponse/PageResponse/MessageResponse`, `cache.CacheService`); each service imports what it needs in `config/CommonConfig.java`. Details: `docs/reports/P2-foundation.md`.

## Các tính năng đã triển khai

- **JWT auth**, shared HMAC secret, validated independently by every service (no per-request call back to auth-service). Refresh token rotation, Redis logout blacklist (fail-open — see Known limitations).
- **Google / Apple Sign-In**: real token verification (Google tokeninfo endpoint; Apple JWKS + RS256 signature check) — needs `GOOGLE_CLIENT_ID` / `APPLE_CLIENT_ID` in `.env` to be strict about audience, works without them too.
- **Dictation**: word-by-word diff with Levenshtein-based typo tolerance, difficulty-weighted scoring.
- **Shadowing**: S3 presigned upload, Azure Pronunciation Assessment via plain REST call (not the native SDK — see Known limitations), red/yellow/green phoneme scoring.
- **Vocabulary SRS**: Anki-style scheduler (learning steps 1m/10m, graduating 1 day, easy 4 days, ease 2.50 floor 1.30, relearning 10m, days start at 04:00 Vietnam time, no daily limits) with Anki sessions (Mới / Đang học / Ôn counters, server-computed button labels) on deck pages and "Ôn tập"; see docs/architecture.md §4.6 and docs/reports/P2-anki.md.
- **Subscriptions**: VNPay payment URL construction + HMAC-SHA512 callback signature verification, activation codes, Premium gating for Pro-only vocab decks (calls subscription-service internally).
- **Admin authorization**: `ADMIN_EMAILS` allow-list (env var) gates lesson/deck write endpoints — a deliberate MVP stand-in for a full roles table.
- Redis caching (lessons, decks, stats) with cache-aside invalidation; standardized error JSON (`error`, `status`, `timestamp`, `requestId`) across every service and the gateway.
- **Frontend (Next.js)**: full user-facing app — auth (email/password + optional Google Sign-In), lesson browsing, dictation practice with word-diff scoring display, shadowing practice with in-browser recording (`MediaRecorder`) and phoneme-level red/yellow/green feedback, vocabulary deck browsing, SRS flashcard review session, notebook, practice history, subscription management (VNPay redirect + activation codes + transaction history), profile settings — plus an Admin section (lesson/sentence/audio CRUD, deck/card CRUD) gated the same way the backend gates it (see limitation below).

## Giới hạn và đơn giản hóa có chủ đích

1. **JWT blacklist is fail-open.** Logged-out tokens are rejected while Redis is up; if Redis is down the blacklist check is skipped (see Operations notes).
2. **No roles table.** Admin endpoints are gated by an `ADMIN_EMAILS` env var comparison, not a real permissions system.
3. **External integrations are optional.** Without AWS/Azure keys, storage and pronunciation scoring run in mock mode (see above). Google/Apple sign-in answer 503 until `GOOGLE_CLIENT_ID` / `APPLE_CLIENT_ID` are set; VNPay and SMTP need real credentials for real payments/emails.
4. **`practice_attempt.sentence_id`** was added to `init-db.sql` (the original schema draft omitted it, but the API needs it to score a specific sentence).
5. Only ports 3000 and 8000 are published; Postgres, Redis and 8001-8005 stay inside the docker network unless you opt in with `docker-compose.override.yml` (bound to 127.0.0.1). The compose file is still a demo setup — put TLS and real secrets in front of it before exposing it to the internet.
6. **The frontend fetches client-side only** (no server-side rendering of authenticated data, no Next.js middleware session). It stores the JWT in a Zustand store persisted to `localStorage` and attaches it via an Axios interceptor; protected routes redirect to `/login` client-side if no token is present after the store hydrates. This matches how the gateway actually authenticates (stateless bearer JWT, not cookies).
7. **Frontend admin-nav visibility is advisory, not a security boundary.** `GET /auth/profile` now returns an `isAdmin` flag (auth-service compares the caller's email against its own `ADMIN_EMAILS`, mirroring the allow-list every other service already enforces independently) and the sidebar only renders the Admin section when it's true — but the actual write endpoints are still, and only, gated by each service's own server-side check. Hitting an admin URL directly while not on the allow-list still gets the backend's real 403, surfaced as an inline error.
8. `VNPAY_RETURN_URL` **must** point at the gateway (`http://localhost:8000/subscriptions/payment-callback`), not directly at a frontend page — the original planning docs had this pointing at `localhost:3000/subscription/callback`, which would have skipped VNPay signature verification entirely and never activated any subscription. Fixed in `.env.example`/`docker-compose.yml`/`application.yml`; `subscription-service` then 302-redirects the browser to `FRONTEND_URL` + `/subscription/success` or `/subscription/failed`.

## Phát triển

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
