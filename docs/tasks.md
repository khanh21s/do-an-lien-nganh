# Kế hoạch task Phase 1: Parroto clone

> Ngày: 2026-10-13. Nguồn: `docs/spec.md` (mã yêu cầu X/AUTH/LES/DIC/SHA/VOC/PRA/SUB/ADM/NAV), hợp đồng kỹ thuật `docs/architecture.md`.
> Mỗi task do **một Coder agent** làm trong worktree riêng (`docs/architecture.md` §10). Coder chỉ sửa file trong "Owner files". Status: `TODO → IN_PROGRESS → REVIEW → DONE` (hoặc `BLOCKED`). Reviewer ghi vào "Review notes".

## Tổng quan

| ID | Title | Wave | Depends | Status |
|---|---|---|---|---|
| T01 | Hạ tầng, Docker, seed, CI | 1 | – | DONE |
| T02 | Bảo mật xuyên service + API Gateway + auth-service | 1 | – | DONE |
| T03 | practice-service: chấm Dictation, Shadowing (S3/Azure + mock), stats | 1 | – | DONE |
| T04 | content-service: CRUD câu, cache, upload, lưu trữ local | 1 | – | DONE-WITH-ISSUES |
| T05 | vocabulary-service + subscription-service: SM-2, Pro gating, VNPay IPN, mã kích hoạt | 1 | – | DONE |
| T06 | Frontend core: API/refresh, types contract, primitives, trang lỗi, tooling | 1 | – | DONE-WITH-ISSUES |
| T07 | Layout & điều hướng (desktop + mobile) | 2 | T06 | DONE |
| T08 | Lessons + Dictation + màn kết quả | 2 | T06 (E2E: T03, T04) | DONE |
| T09 | Shadowing frontend (Recorder WAV) | 2 | T06 (E2E: T01, T03) | DONE |
| T10 | Vocabulary frontend (flashcard, review, notebook) | 2 | T06 (E2E: T05) | DONE |
| T11 | Dashboard, lịch sử luyện tập, hồ sơ | 2 | T06 (E2E: T02, T03) | DONE |
| T12 | Subscription, pricing & Admin frontend | 2 | T06 (E2E: T04, T05) | DONE |

- **Lệnh verify:** `scripts/verify-fe.sh`, `scripts/verify-gw.sh`, `scripts/verify-java.sh <svc>` do T01 tạo. Khi T01 chưa merge (mọi task Wave 1), chạy lệnh `docker run …` tương ứng ở `docs/architecture.md` §1.1 — máy này không có `npm`/`mvn`, Java local là 17.
- **Wave 1** (T01–T06): độc lập hoàn toàn, chạy song song từ `dev/multi-agent` hiện tại. Không task nào sửa file của task khác.
- **Wave 2** (T07–T12): bắt đầu sau khi **T06 đã merge** (phụ thuộc cứng: types/endpoints/primitives). Phụ thuộc "E2E" chỉ cần cho kiểm thử tích hợp, không chặn việc code (FE code theo hợp đồng `architecture.md` §4.5); nên bắt đầu Wave 2 sau khi cả Wave 1 đã merge nếu có thể.

### Thứ tự merge
1. Wave 1: **T01 → T02 → T03 → T04 → T05 → T06** (T01 trước vì đổi compose/env mà E2E của các task sau cần; T02 trước các service vì đổi `SecurityConfig`/`application.yml` block `jwt:`).
2. Chạy E2E tích hợp Wave 1 (`docker-compose up -d --build`, AC của X, AUTH-02..04, SUB-01/02, VOC-01/03, DIC-07, SHA-04 qua curl).
3. Wave 2: **T07 → T08 → T09 → T10 → T11 → T12** (T07 trước vì shell/nav; T08 trước T09 vì màn kết quả chung).
4. E2E toàn bộ AC spec Phase 1.

## File dùng chung & quyền sở hữu

| File / vùng | Chủ sở hữu | Quy tắc cho task khác |
|---|---|---|
| `docker-compose.yml`, `docker-compose.override.yml.example`, `.env.example`, `init-db.sql`, `initdb/**`, `media/seed/**`, `scripts/**`, `.github/**`, `.gitignore`, `README.md`, `HUONG_DAN_CHAY_DU_AN.md`, `services/*/Dockerfile` | **T01** | Không sửa. Env mới đã được liệt kê sẵn trong hợp đồng (§5.3, §8.1) và T01 wire trước. Cần env khác → ghi Review notes. |
| `api-gateway/**` (gồm `server.js`, `package.json`, lockfile, Dockerfile) | **T02** | Không sửa. Route `/subscriptions/vnpay-ipn` public do T02 thêm. |
| Toàn bộ `services/auth-service/**` | **T02** | – |
| `services/{content,practice,vocabulary,subscription}-service/.../security/*.java` **hiện có**, `config/SecurityConfig.java`, `service/CacheService.java` | **T02** | Task service không sửa. Ngoại lệ: T05 **tạo mới** `subscription/security/AdminGuard.java` (file mới, không xung đột). |
| `services/*/src/main/resources/application.yml` | Theo **block**: `jwt:` + block mới `internal:` (ngay dưới `jwt:`) → **T02**; `spring.servlet.multipart` + block `app:` cuối file (content) → **T04**; `aws:`/`azure:` + block `app:` cuối file (practice) → **T03**; `vnpay:`/`admin:` (subscription) → **T05** | Chỉ sửa block của mình; không định dạng lại file. Khi rebase xung đột: giữ cả hai block. |
| `services/<svc>/**` còn lại | content → **T04**; practice → **T03**; vocabulary + subscription → **T05** | – |
| `services/*/pom.xml` | Chủ sở hữu service tương ứng (auth → T02) | Phase 1 không cần dependency mới; nếu cần → Review notes. |
| FE `src/lib/api.ts`, `lib/types.ts`, `lib/endpoints/*.ts`, `lib/queryKeys.ts`, `lib/constants.ts`, `lib/storageKeys.ts`, `lib/practice-session.ts`, `lib/practice-nav.ts`, `src/hooks/**`, `components/ui/**`, `components/layout/AuthGuard.tsx`, `components/practice/{SentencePlayer,PracticeHeader,ShortcutHelp}.tsx`, `components/audio/RecordingPlayer.tsx`, `app/layout.tsx`, `app/providers.tsx`, `app/not-found.tsx`, `app/error.tsx`, `app/(app)/error.tsx`, `app/login/**`, `app/register/**`, `store/**`, `package.json`, `package-lock.json`, `next.config.js`, `tsconfig.json`, `.eslintrc.json`, `vitest.config.ts` | **T06** | Wave 2: chỉ **import**. Nếu thật sự thiếu type/endpoint: thêm vào **vùng append-only** ở cuối `lib/types.ts` / file endpoint tương ứng, dạng `// --- T0x additions ---` … `// --- end T0x ---`, không sửa dòng phía trên. Không thêm dependency npm. |
| `components/layout/{AppShell,Sidebar,Topbar,PublicHeader,MobileTabBar,MoreDrawer}.tsx`, `app/(app)/layout.tsx`, `app/lessons/layout.tsx`, `app/pricing/layout.tsx` | **T07** | Task khác không sửa; badge/streak cập nhật qua invalidate query key (`qk.vocabStats`, `qk.streak`). |
| `components/audio/AudioPlayer.tsx`, `components/practice/WordDiffView.tsx` | **T08** | T11 dùng `AudioPlayer` (không đổi props hiện có). |
| `components/audio/Recorder.tsx`, `components/practice/PhonemeScoreView.tsx` | **T09** | T11 dùng `PhonemeScoreView` (không đổi props hiện có). |
| `components/practice/Flashcard.tsx` | **T10** | – |
| `docs/spec.md`, `docs/architecture.md`, `docs/tasks.md` | Architect / Orchestrator | Coder chỉ cập nhật Status + Review notes của **task mình** (vùng riêng, không xung đột). |

---

## T01 – Hạ tầng, Docker, seed, CI

- **Branch:** `feat/T01-infra-devops`
- **Wave:** 1 · **Depends on:** –
- **Spec items:** X-01, X-03 (phần compose), X-04, X-05 (Java Dockerfile), X-07 (CI, `.gitignore`), LES-08, ADM-08, quyết định giá gói (spec Q7), wiring mock mode (architecture §8.4)
- **Owner files:** `docker-compose.yml`, `docker-compose.override.yml.example` (mới), `.env.example`, `init-db.sql`, `initdb/` (mới), `media/seed/` (mới), `scripts/` (mới), `.github/workflows/ci.yml` (mới), `.gitignore`, `README.md`, `HUONG_DAN_CHAY_DU_AN.md`, `services/*/Dockerfile`, `frontend/tsconfig.tsbuildinfo` (xoá khỏi git)

**Các bước**
1. `docker-compose.yml`: bỏ `ports` của postgres, redis, 5 service (chỉ giữ `3000`, `8000`); tạo `docker-compose.override.yml.example` publish lại 5432/6379/8001–8005 cho dev (README hướng dẫn copy thành `docker-compose.override.yml`, file này vào `.gitignore`).
2. Bỏ mọi default của `JWT_SECRET` trong compose → `${JWT_SECRET:?JWT_SECRET is required}`; tương tự `INTERNAL_TOKEN` cho auth, subscription, vocabulary. `JWT_EXPIRY_SECONDS`/`JWT_REFRESH_EXPIRY_SECONDS` lấy từ `.env` (`${JWT_EXPIRY_SECONDS:-3600}`…). Truyền `APPLE_CLIENT_ID` vào auth-service; `ADMIN_EMAILS` vào subscription-service; `REDIS_URL=redis://redis:6379` vào api-gateway (+ `depends_on: redis healthy`).
3. Mock mode: thêm `STORAGE_PROVIDER`, `SPEECH_PROVIDER` (mặc định `auto`), `MEDIA_ROOT=/data/media` cho content & practice; volume `media_data:/data/media` cho cả hai; bind `./media/seed:/data/media/seed:ro` cho content.
4. X-04: chuyển tạo role/DB sang `initdb/00-roles.sh` (đọc `AUTH_DB_PASSWORD`…, dùng `psql -v ON_ERROR_STOP=1 -v pw=...` và `:'pw'`), mount `./initdb/00-roles.sh` → `/docker-entrypoint-initdb.d/00-roles.sh`, `init-db.sql` → `01-init.sql` không còn `CREATE ROLE … PASSWORD 'hard-code'`. Truyền các biến `*_DB_PASSWORD` vào container postgres.
5. Seed `init-db.sql` (schema giữ nguyên từng ký tự): giá gói 79.000 / 169.000 / 499.000, bỏ `plan-lifetime`; 3 mã kích hoạt demo (architecture §4.3); `audio_url` seed → `local:seed/lesson-<n>/full.mp3` và `sentence.audio_url` → `local:seed/lesson-<n>/s<order>.mp3`.
6. `scripts/gen-seed-audio.sh`: sinh audio mẫu bằng TTS chạy trong Docker (vd image có `espeak-ng` + `ffmpeg`) từ text câu seed, xuất mp3 vào `media/seed/lesson-<n>/` và file `full.mp3` ghép; commit file sinh ra (mỗi file vài chục KB). Nếu không sinh được TTS → ghi Review notes và để `sentence.audio_url = NULL` (FE fallback).
7. Java Dockerfile ×5: `maven:3.9-eclipse-temurin-21` / `eclipse-temurin:21-jre-alpine`; tạo `/data/media` thuộc `appuser` (content, practice).
8. `scripts/verify-fe.sh`, `scripts/verify-gw.sh`, `scripts/verify-java.sh <svc>` = các lệnh ở architecture §1.1 (chmod +x).
9. `.github/workflows/ci.yml`: job `frontend`, `gateway`, `java` (matrix 5 service) như architecture §9.
10. `.gitignore`: `frontend/tsconfig.tsbuildinfo`, `*.tsbuildinfo`, `docker-compose.override.yml`, `media/*` trừ `media/seed/`; `git rm --cached frontend/tsconfig.tsbuildinfo`.
11. `.env.example`: thêm `INTERNAL_TOKEN`, `STORAGE_PROVIDER`, `SPEECH_PROVIDER`, `MEDIA_ROOT`, `REDIS_URL`, `APPLE_CLIENT_ID`; JWT_SECRET mẫu ≥ 32 ký tự + lệnh `openssl rand -base64 48`; S3/Azure để trống kèm chú thích "để trống = chế độ mock".
12. README + HUONG_DAN: cách chạy demo không key (mock), `ADMIN_EMAILS` + đăng ký tài khoản admin demo (`admin@parroto.com`), mã kích hoạt demo, lệnh chuẩn hoá `score > 100` cũ, cách test IPN bằng request có chữ ký, lưu ý Redis fail-open.

**Acceptance criteria**
- [ ] `docker-compose config` hợp lệ khi `.env` có đủ biến; báo lỗi rõ khi thiếu `JWT_SECRET`.
- [ ] Cấu hình mặc định: chỉ cổng host 3000 và 8000 (`docker-compose config | grep -A2 ports`).
- [ ] Volume mới + đổi `AUTH_DB_PASSWORD` trong `.env` → auth-service kết nối DB được.
- [ ] `init-db.sql` không còn mật khẩu hard-code; diff phần `CREATE TABLE` = rỗng.
- [ ] `/subscriptions/plans` trả đúng 3 gói 79k/169k/499k (sau E2E).
- [ ] Dockerfile Java dùng 21; `ci.yml` parse được (`python3 -c "import yaml,sys;yaml.safe_load(open('.github/workflows/ci.yml'))"`).
- [ ] `frontend/tsconfig.tsbuildinfo` không còn được track.

**Verify**
```bash
docker-compose config -q && echo OK
bash -n scripts/*.sh initdb/*.sh
python3 -c "import yaml;yaml.safe_load(open('.github/workflows/ci.yml'))"
# E2E (worktree tích hợp): docker-compose down -v && docker-compose up -d --build && docker-compose ps
```

**Status:** DONE

**Review notes:**
- Review Wave 1 (2026-10-13): all ACs pass. `docker-compose config -q` OK; only 3000/8000 published; `down -v` + `up -d --build` with the user's DB passwords → all services healthy; plans 79k/169k/499k, no lifetime; seed audio `local:seed/...` plays (200 `audio/mpeg`, Range 206); demo codes work; `scripts/vnpay-ipn-test.sh` drives 97/04/00/02/01 correctly.
- Note (Low, docs): `.env` files that predate T01 keep `your_…` placeholders (e.g. `GOOGLE_CLIENT_ID`). Practice treats `your_…` as missing; auth does not (see T02). Consider telling users in README to blank unused keys.

---

## T02 – Bảo mật xuyên service + API Gateway + auth-service

- **Branch:** `feat/T02-security-gateway`
- **Wave:** 1 · **Depends on:** –
- **Spec items:** X-02, X-03, X-05 (gateway), X-06, AUTH-02, AUTH-03, AUTH-04, AUTH-07 (BE), SUB-02 (route GW + permitAll)
- **Owner files:** `api-gateway/**`; `services/auth-service/**`; trong content/practice/vocabulary/subscription: `security/JwtValidator.java`, `security/JwtAuthFilter.java`, `security/InternalTokenFilter.java` (mới, chỉ subscription), `config/SecurityConfig.java`, `service/CacheService.java`, block `jwt:` + `internal:` của `application.yml`

**Các bước**
1. **Gateway** `middleware/auth.js`: bỏ fallback secret; khi load module kiểm `JWT_SECRET` ≥ 32 ký tự, không thì log + `process.exit(1)`. `verifyToken` từ chối `payload.type !== 'access'`; kiểm `token_blacklist:{jti}` qua `middleware/redis.js` (node-redis `redis@4.6.14`, `REDIS_URL`, kết nối lazy, lỗi → fail-open + warn).
2. Gateway `server.js`: thêm `/subscriptions/vnpay-ipn` vào nhóm public cùng `plans`/`payment-callback`. `package.json` pin chính xác mọi version, sinh `package-lock.json` (bằng `docker run node:20-alpine npm install --package-lock-only`), Dockerfile dùng `npm ci --omit=dev`.
3. **Java (5 service)**: `JwtValidator` (và `JwtService` của auth) ném `IllegalStateException` khi secret rỗng/< 32 byte; `JwtAuthFilter` chỉ chấp nhận `type=access`, kiểm blacklist bằng `StringRedisTemplate.hasKey("token_blacklist:"+jti)` (lỗi Redis → fail-open + WARN). `application.yml`: `jwt.secret: ${JWT_SECRET:}`; thêm `internal.token: ${INTERNAL_TOKEN:}` ngay dưới `jwt:`.
4. X-02: `InternalTokenFilter` cho auth-service và subscription-service, chỉ áp `/internal/**`, so sánh hằng thời gian; token rỗng lúc khởi động → fail. `SecurityConfig` đăng ký filter; giữ `permitAll` cho `/internal/**` ở tầng Spring Security nhưng filter chặn trước.
5. Subscription `SecurityConfig`: `permitAll` cho `GET /subscriptions/vnpay-ipn`.
6. X-06: `CacheService.invalidateByPattern` ở 4 service dùng `SCAN` (`ScanOptions.match(pattern).count(500)`) + `DEL` theo lô.
7. AUTH-04: `GoogleTokenVerifier`/`AppleTokenVerifier` khi client id rỗng → `ApiException(SERVICE_UNAVAILABLE, "Google login chưa cấu hình")` (Apple tương tự), không bao giờ bỏ qua `aud`.
8. AUTH-07: `ProfileService.changePassword` → 400 "Tài khoản đăng nhập bằng Google/Apple không có mật khẩu" khi `authProvider != local`.
9. Unit test: `auth-service` `JwtServiceTest` hoặc `JwtValidatorTest` ở 1 service: token `type=refresh` bị từ chối; secret ngắn → exception.

**Acceptance criteria**
- [ ] `JWT_SECRET=` → GW thoát với log rõ; service Java không khởi động được (log `IllegalStateException`).
- [ ] Refresh token làm Bearer gọi `GET /practice/stats` (qua GW) và gọi thẳng service → 401.
- [ ] Logout rồi dùng token cũ gọi `/practice/stats`, `/vocabs/user/stats`, `/subscriptions/user` → 401 cả ba.
- [ ] `curl auth-service:8001/internal/users/<id>` không header → 401; có `X-Internal-Token` đúng → 200 (từ container khác).
- [ ] `GOOGLE_CLIENT_ID` rỗng → `POST /auth/login-google` 503.
- [ ] Không còn lời gọi `keys(` trong `CacheService` (`grep -rn "\.keys(" services/*/src/main` rỗng).
- [ ] `/subscriptions/vnpay-ipn` gọi không token tới được subscription-service (không 401 ở GW).

**Verify**
```bash
scripts/verify-gw.sh   # hoặc lệnh Gateway ở architecture §1.1
for s in auth-service content-service practice-service vocabulary-service subscription-service; do
  docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -v "$HOME/.m2":/tmp/.m2 -v "$PWD/services/$s":/app -w /app \
    maven:3.9-eclipse-temurin-21 mvn -B -q -Dmaven.repo.local=/tmp/.m2/repository test || exit 1; done
grep -rn "dev_super_secret\|\.keys(" api-gateway services/*/src/main || echo CLEAN
```

**Status:** DONE

**Review notes:**
- **Fixed by reviewer (Blocker) – 3350240:** auth-service `JwtService.generate` used `.signWith(key)`, so jjwt picked **HS512** for a ≥ 64-byte `JWT_SECRET` (the user's is 68 chars). The gateway only accepts `algorithms: ['HS256']` (`api-gateway/middleware/auth.js:33`), so every authenticated call got 401 "Invalid or expired token". Repro before fix: `curl -s -XPOST :8000/auth/login -d '{…}' | jq -r .token | cut -d. -f1 | base64 -d` → `{"alg":"HS512"}`; `curl -H "Authorization: Bearer $T" :8000/practice/stats` → 401. Now pinned to `Jwts.SIG.HS256` (`services/auth-service/src/main/java/com/parroto/auth/security/JwtService.java:67-69`) + unit test `alwaysSignsWithHs256EvenForLongSecrets`.
- After the fix all T02 ACs pass: refresh token as Bearer → 401 at GW and directly at practice-service; logout → old access token 401 on `/practice/stats`, `/vocabs/user/stats`, `/subscriptions/user`, `/auth/profile` (GW) and directly at vocabulary-service; `/internal/**` without / with wrong `X-Internal-Token` → 401, correct token → 200 (from inside the network); GW `/internal` → 404; `/subscriptions/vnpay-ipn` reachable without token; GW exits 1 on empty/short `JWT_SECRET`; no `.keys(`.
- Open (Medium, not an AC): **logout does not revoke the refresh token.** `AuthService.logout` (`services/auth-service/src/main/java/com/parroto/auth/service/AuthService.java:123-131`) only blacklists the access-token `jti`; `POST /auth/logout` takes no body. Repro: login → logout with Bearer → `curl -XPOST :8000/auth/refresh-token -d '{"refreshToken":"<old>"}'` → 200 with new tokens. Suggest accepting an optional `{refreshToken}` body on logout and blacklisting its `jti` too (FE `authApi.logout` must send it).
- Open (Low): `GoogleTokenVerifier`/`AppleTokenVerifier` only treat a *blank* client id as "not configured" (`GoogleTokenVerifier.java:36`); a `your_…` placeholder (present in the user's old `.env`) → 401 "Invalid Google token" instead of 503, and FE may show the Google button. Either reuse practice's placeholder rule or blank the value in `.env`.
- Review Final (FIX-R1 `52f9946`, `a9714fe`, `138a5bd`): logout with `{refreshToken}` → old refresh token 401 (API + from the FE avatar menu in the browser); logout without body still 200; placeholder `your_…` Google client id → 503. Medium + Low issues above are **resolved**.
- Review Final fix `208b400` (High, found in browser): helmet's default `Cross-Origin-Resource-Policy: same-origin` on the gateway made Chrome block every `<audio src=http://localhost:8000/content/…/audio>` loaded from the FE origin (`ERR_BLOCKED_BY_RESPONSE.NotSameOrigin`) — lesson/sentence audio never played. Now `crossOriginResourcePolicy: cross-origin` (`api-gateway/server.js`); `requireAuthForWrites` also lets HEAD/OPTIONS through.

---

## T03 – practice-service: chấm Dictation, Shadowing (S3/Azure + mock), stats

- **Branch:** `feat/T03-practice-backend`
- **Wave:** 1 · **Depends on:** –
- **Spec items:** DIC-07, SHA-02 (BE), SHA-04, PRA-03 (BE), PRA-05 (BE: `difficulty`), PRA-07 (BE), X-07 (test `DictationScorer`, `StreakCalculator`), mock mode practice (architecture §8.2)
- **Owner files:** `services/practice-service/**` **trừ** `security/*`, `config/SecurityConfig.java`, `service/CacheService.java`, block `jwt:`/`internal:` của `application.yml`. Mới: `service/storage/*`, `service/speech/*`, `config/StorageConfig.java`, `util/AppTime.java`, `util/WavUtils.java`, `src/test/java/**`

**Các bước**
1. DIC-07: `DictationScorer.weightedScore` → Easy 0.6 / Normal 0.8 / Hard 1.0, kẹp 0–100 (architecture §4.7).
2. Storage: tách `ShadowingStorageService` thành `RecordingStorage` + `S3RecordingStorage` + `LocalRecordingStorage`; `StorageConfig` chọn theo `app.storage.provider` (auto/s3/local), S3 bean chỉ tạo khi s3; log provider khi khởi động.
3. Endpoint mới `PUT /practice/shadowing/recordings/{recordingId}` (local only, ≤ 2 MB, `WavUtils.validate`, recordingId phải là UUID) → 204; s3 mode → 404.
4. `ShadowingUploadUrlResponse` + `uploadMethod, uploadRequiresAuth, contentType, storage` theo hợp đồng.
5. Speech: interface `PronunciationScorer`; `AzureSpeechClient` implements (giữ header WAV PCM 16k); `MockPronunciationScorer` theo §8.2; `SPEECH_PROVIDER` auto/azure/mock. `ShadowingAttemptResponse` + `scorer`. Validate WAV trước khi chấm → 400 tiếng Việt.
6. SHA-04: `PracticeAttemptRepository.findByIdAndUserId`; `streamRecording` nhận user, khác chủ → 404; controller truyền principal.
7. PRA-03: `GET /practice/stats/activity?days=` (1–365, mặc định 90) — native query group by `date(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')`, điền ngày trống, `minutes = round(sum(duration)/60)`. DTO `ActivityDayResponse`.
8. PRA-07: `StreakCalculator`/`StatsService` dùng `AppTime.ZONE` cho "hôm nay" và quy đổi ngày của attempt.
9. PRA-05: `AttemptSummaryResponse` + `difficulty`.
10. Unit test: `DictationScorerTest`, `StreakCalculatorTest`, `WavUtilsTest`, `MockPronunciationScorerTest` (deterministic: cùng input → cùng điểm; im lặng → < 40).
11. `application.yml`: block `app.storage`, `app.speech` cuối file (không đụng `jwt:`).

**Acceptance criteria**
- [ ] Hard đúng 100% → `score = 100`; Normal 100% → 80; Easy 100% → 60; không input nào cho score > 100 (test).
- [ ] Không có key AWS/Azure: request-upload-url trả `storage=local`, `uploadRequiresAuth=true`; PUT WAV hợp lệ 204; attempt trả `scorer=mock` với điểm từng từ.
- [ ] Gửi file WebM/không phải WAV 16k mono → 400 thông điệp rõ.
- [ ] Có key S3: URL presigned ký `Content-Type: audio/wav` (giữ hành vi cũ, đã kiểm bằng code review).
- [ ] User B gọi recording của A → 404.
- [ ] `/practice/stats/activity?days=90` → đúng 90 phần tử; `days=400` → 400; attempt 23:30 giờ VN được tính đúng ngày (test hoặc SQL).
- [ ] `mvn test` xanh.

**Verify**
```bash
scripts/verify-java.sh practice-service   # hoặc lệnh Java ở architecture §1.1 với <svc>=practice-service
```

**Status:** DONE

**Review notes:**
- Review Wave 1: all ACs verified E2E via gateway. Dictation perfect answer Easy 60 / Normal 80 / Hard 100; request-upload-url → `storage=local, uploadRequiresAuth=true, uploadMethod=PUT`; PUT WebM → 400, PUT 48 kHz WAV → 400, PUT without Bearer → 401, PUT 16 kHz mono WAV → 204; submit → 201 `scorer=mock` with word scores; owner streams recording (RIFF) 200, other user → 404; `activity?days=90` → 90 entries (today counts attempts), `days=400` → 400; `/practice/attempts` without `lessonId` → 200 with `difficulty`; `/practice/streak` current ≥ 1. No issues found.

---

## T04 – content-service: CRUD câu, cache, upload, lưu trữ local

- **Branch:** `feat/T04-content-backend`
- **Wave:** 1 · **Depends on:** –
- **Spec items:** LES-07, ADM-02 (BE kiểm tra), ADM-03 (BE), ADM-04 (BE), LES-08 (đọc `local:`), mock storage content (architecture §8.3)
- **Owner files:** `services/content-service/**` **trừ** `security/*`, `config/SecurityConfig.java`, `service/CacheService.java`, block `jwt:`/`internal:` của `application.yml`. Mới: `service/storage/*`, `config/StorageConfig.java`, `dto/UpdateSentenceRequest.java`, `src/test/java/**`

**Các bước**
1. Tách `S3StorageService` → `LessonAudioStorage` + `S3LessonAudioStorage` + `LocalLessonAudioStorage`; định tuyến đọc theo tiền tố `local:`; Content-Type theo đuôi; Range 206 cho local. `AudioController` dùng interface.
2. ADM-04: `max-file-size`/`max-request-size` 50MB; validate `file.getContentType()` bắt đầu `audio/` + đuôi cho phép → 400 "File phải là audio (mp3, m4a, wav, ogg, webm)".
3. ADM-03: `PUT /lessons/{id}/sentences/{sentenceId}` (partial update, validate `startMs < endMs` nếu cả hai có, sentence phải thuộc lesson → 404), `DELETE /lessons/{id}/sentences/{sentenceId}` + đánh lại `sentence_order` 1..N trong cùng transaction. Admin.
4. LES-07: helper `evictLessonCaches(lessonId)` xoá `lesson:{id}` + `lessons:list:*` (+ key search nếu có) sau `addSentences`, update/delete câu, update/delete/upload-audio bài.
5. ADM-02: xác nhận `UpdateLessonRequest` (`title, description, topicTag, cefrLevel`) hoạt động + xoá cache; validate `cefrLevel` A1–C2.
6. Unit test: `LocalLessonAudioStorageTest` (ghi/đọc/Range, chặn `..`), test đánh lại thứ tự câu (thuần Java, mock repository bằng Mockito).

**Acceptance criteria**
- [ ] Không key AWS: admin upload mp3 20MB → 200, `GET /content/lessons/{id}/audio` phát được, hỗ trợ `Range`.
- [ ] Upload `.exe`/`application/octet-stream` → 400.
- [ ] `GET /content/lessons/{id}/audio` với seed `local:seed/...` → 200 `audio/mpeg`.
- [ ] Sửa `startMs/endMs` → `GET /content/lessons/{id}` trả giá trị mới ngay (cache bị xoá).
- [ ] Xoá câu 2/5 → còn 4 câu, `sentenceOrder` = 1,2,3,4; `/content/lessons` hiện `sentenceCount` mới ngay.
- [ ] User thường gọi PUT/DELETE câu → 403.
- [ ] `mvn test` xanh.

**Verify**
```bash
scripts/verify-java.sh content-service
```

**Status:** DONE-WITH-ISSUES

**Review notes:**
- Review Wave 1: ACs pass E2E — admin upload 20 MB mp3 → 200 and streams with Range 206; `.exe`/`application/octet-stream` → 400; seed `local:seed/...` → 200 `audio/mpeg`; PUT sentence `endMs` → detail shows new value immediately; `startMs >= endMs` → 400; delete sentence 2/5 → orders 1..4 and list `sentenceCount` 4 immediately; normal user PUT/DELETE sentence and POST lesson → 403. (Tested on a throw-away lesson created by admin, then deleted; seed lessons untouched.)
- Open (Low): malformed path params give 500 instead of 400/404 — `curl :8000/content/lessons/abc` → 500 "Internal server error". `GlobalExceptionHandler` (`services/content-service/src/main/java/com/parroto/content/exception/GlobalExceptionHandler.java:55`) has no `MethodArgumentTypeMismatchException` / `NoResourceFoundException` mapping (practice-service has one at `GlobalExceptionHandler.java:43`).
- Review Final (FIX-R1 `37e6478`): `/content/lessons/abc` → 400, unknown content route → 404 JSON. Low issue above **resolved**.
- Open (Low): `HEAD /content/sentences/{id}/audio` → 401 (Spring `SecurityConfig` permits only `HttpMethod.GET`); browsers use GET, so no user impact.

---

## T05 – vocabulary-service + subscription-service: SM-2, Pro gating, VNPay IPN, mã kích hoạt

- **Branch:** `feat/T05-vocab-subscription-backend`
- **Wave:** 1 · **Depends on:** –
- **Spec items:** VOC-01, VOC-03, VOC-04 (BE `userVocabId`), VOC-07 (BE fields review-queue), VOC-08/SUB-04, SUB-01, SUB-02, SUB-05, ADM-05 (BE delete card), ADM-06 (BE), ADM-07, X-02 (phía client: header `X-Internal-Token`), X-07 (test SM2, VNPay)
- **Owner files:** `services/vocabulary-service/**` và `services/subscription-service/**` **trừ** `security/*` hiện có, `config/SecurityConfig.java`, `service/CacheService.java`, block `jwt:`/`internal:` của `application.yml`. Mới: `subscription/security/AdminGuard.java`, `subscription/controller/AdminActivationCodeController.java`, DTO mới, `src/test/java/**`

**Các bước — vocabulary**
1. VOC-01: `SM2Calculator` theo SM-2 chuẩn (architecture §4.6) trả `{interval, easeFactor, repetitions}`; `ReviewService.submitReview` dùng kết quả (reset reps khi q<3, `next_review_at = now + interval` ngày); message theo interval.
2. VOC-03: `addFromLesson` kiểm deck của thẻ `isProOnly` → `PremiumClient.isPremium(userId)` false → 403 "Bộ từ Pro – nâng cấp Premium để học".
3. `PremiumClient` gửi `X-Internal-Token` (`${internal.token}`); giữ key `premium:{userId}`.
4. VOC-04: `GET /vocabs/decks/{id}/cards` khi có principal → set `userVocabId` (1 query `findByUserIdAndCardIdIn`).
5. VOC-07: `ReviewQueueItem` + `exampleVi, partOfSpeech, audioUrl, imageUrl`.
6. ADM-05: `DELETE /vocabs/cards/{id}` (admin) giảm `card_count` trong transaction; xoá cache deck. ADM-07: `createDeck` gán `createdBy = principal.userUuid()`.
7. `SM2CalculatorTest`: 4 mức × (n=0, n=1, n=3/I=10/EF=2.5), EF floor 1.3.

**Các bước — subscription**
8. SUB-01/02: tách `VNPayService.verifySignature(params)` thuần; `processVnpayResult(params)` dùng chung cho callback và IPN: chữ ký sai → 97; không tìm thấy → 01; đã xử lý (status ≠ pending) → 02; `vnp_Amount != amount*100` → đánh `failed`, 04; `vnp_ResponseCode=00 && vnp_TransactionStatus=00` → `success` + kích hoạt; idempotent (khoá dòng/điều kiện `status='pending'`). `GET /subscriptions/vnpay-ipn` trả `{RspCode, Message}`; `payment-callback` chỉ redirect theo trạng thái (nếu IPN chưa tới thì xử lý như IPN rồi redirect).
9. SUB-04: sau khi kích hoạt (thanh toán/mã) `redisTemplate.delete("premium:" + userId)` (afterCommit).
10. SUB-05: cộng dồn: đang active → `end_at += duration`, ngược lại `start=now, end=now+duration`; lỗi theo hợp đồng §4.5.
11. ADM-06: `AdminGuard` (ADMIN_EMAILS, block `admin:` yml), `POST/GET /subscriptions/admin/activation-codes` (quantity ≤ 500, code `PROMO-XXXXX-XXXXX` duy nhất với retry khi trùng, `expiresAt` mặc định +365 ngày); `usedByEmail` lấy qua `AuthServiceClient` (gửi `X-Internal-Token`, lỗi → null).
12. `VNPayServiceTest`: ký + verify đúng, sửa 1 tham số → sai, số tiền lệch → 04 (test service logic bằng Mockito repository).

**Acceptance criteria**
- [ ] Test SM-2: n=3,I=10,EF=2.5 → Again I=1,n=0,EF=1.96; Hard 25/2.36; Good 25/2.50; Easy 25/2.60; n=1 Good → 6.
- [ ] User free `POST /vocabs/user/add-from-lesson` thẻ deck Pro → 403.
- [ ] `GET /vocabs/decks/{id}/cards` có Bearer → thẻ đã học có `userVocabId`.
- [ ] Xoá thẻ → `card_count` giảm 1.
- [ ] IPN chữ ký hợp lệ nhưng sai số tiền → giao dịch `failed`, subscription không đổi, `RspCode=04`; gọi IPN thành công 2 lần → lần 2 `RspCode=02`, chỉ 1 subscription.
- [ ] Kích hoạt mã → key `premium:{userId}` bị xoá; đang premium đến D + mã 30 ngày → D+30; mã đã dùng → 400 "Mã đã được sử dụng".
- [ ] Tạo 10 mã → 10 mã duy nhất; danh sách lọc `used=true` hiện email/ngày.
- [ ] `mvn test` xanh ở cả hai service.

**Verify**
```bash
scripts/verify-java.sh vocabulary-service && scripts/verify-java.sh subscription-service
```

**Status:** DONE

**Review notes:**
- Review Wave 1: ACs pass E2E — free user adds Pro-deck card → 403; deck cards with Bearer expose `userVocabId`; reviews with q=1/3/4/5 → 200, 2nd Good → interval 6, Again → interval 1 / repetitions 0; review-queue items carry `exampleVi, partOfSpeech, audioUrl, imageUrl`; admin delete card → `cardCount` 3→2; normal user DELETE card / POST deck → 403. Subscription: `PROMO-DEMO1-MONTH` → premium, reuse → 400 "Mã đã được sử dụng", unknown → 404, 2nd code accumulates (119 days); `premium:{userId}` key deleted on activation and Pro card then allowed (201); admin creates 10 unique `PROMO-` codes, `used=true` list shows `usedByEmail`; normal user → 403 on admin endpoints. VNPay via `scripts/vnpay-ipn-test.sh`: bad signature 97, wrong amount 04 (no subscription), valid 00 → premium, replay 02, unknown txn 01.
- Open (Low): vocabulary-service returns 500 for malformed UUIDs / unmapped routes — `curl :8000/vocabs/decks/xyz/cards` → 500, `curl :8000/vocabs/decks/22222222-2222-2222-2222-222222222201` (no `GET /vocabs/decks/{id}` route) → 500. `GlobalExceptionHandler` (`services/vocabulary-service/src/main/java/com/parroto/vocabulary/exception/GlobalExceptionHandler.java:38`) maps everything unknown to 500; add `MethodArgumentTypeMismatchException` → 400 and `NoResourceFoundException`/`ErrorResponseException` → their own status.
- Review Final (FIX-R1 `37e6478`, `a1e65f7`): `/vocabs/decks/xyz/cards` → 400, `GET /vocabs/decks/{id}` → 405 JSON (no 500); `GET /vocabs/user/learned` returns `nextReviewAt`, `intervalDays`, `easeFactor`. VNPay IPN re-run: 97/04/00/02. Low issue above **resolved**.

---

## T06 – Frontend core: API/refresh, types contract, primitives, trang lỗi, tooling

- **Branch:** `feat/T06-frontend-core`
- **Wave:** 1 · **Depends on:** –
- **Spec items:** AUTH-01, AUTH-04 (FE: nút Google ẩn khi chưa cấu hình – xác nhận), AUTH-05, NAV-02, X-07 (FE: eslint, bỏ ignoreDuringBuilds, vitest), X-08, SHA-05 (component `RecordingPlayer`), ADM-02 (sửa type `updateLesson`), nền tảng cho LES-01/DIC-03/DIC-05
- **Owner files:** xem bảng "File dùng chung" (dòng T06): `lib/api.ts`, `lib/types.ts`, `lib/endpoints/*.ts`, `lib/queryKeys.ts`, `lib/constants.ts`, `lib/storageKeys.ts`, `lib/practice-session.ts`, `lib/practice-nav.ts`, `hooks/**`, `components/ui/**`, `components/layout/AuthGuard.tsx`, `components/practice/{SentencePlayer,PracticeHeader,ShortcutHelp}.tsx`, `components/audio/RecordingPlayer.tsx`, `components/auth/**`, `app/{layout,providers,not-found,error}.tsx`, `app/(app)/error.tsx`, `app/login/**`, `app/register/**`, `store/**`, `package.json`, `package-lock.json`, `next.config.js`, `tsconfig.json`, `.eslintrc.json`, `vitest.config.ts`

**Các bước**
1. AUTH-01: interceptor refresh single-flight (`createRefreshQueue` thuần, có test); store thêm `setTokens`.
2. AUTH-05: `AuthGuard` redirect `/login?next=<path+search>`; `/login` & `/register` đọc `next` (chỉ chấp nhận path nội bộ bắt đầu `/`, không `//`), đã đăng nhập → `next || /dashboard`.
3. Contract-first: cập nhật `types.ts` + `endpoints/*.ts` cho **toàn bộ** mục architecture §4.5: `ActivityDay`, `AttemptSummary.createdAt/difficulty`, `ShadowingUploadUrlResponse` mới, `ShadowingAttemptResult.scorer`, `LessonStats`/`LessonAttempts` (mirror DTO BE hiện có), `StreakResponse`, `TrendingError`, `UpdateLessonInput`, `UpdateSentenceInput`, `CardInput.partOfSpeech`, `VocabCard.userVocabId`, `ReviewQueueItem` fields mới, `ActivationCode`, `CreateActivationCodesInput`, bỏ `autoRenew`; hàm mới: `practiceApi.{getActivity, getStreak, getTrendingErrors, getLessonStats, getLessonAttempts, uploadRecording, getRecordingBlob}`, `contentApi.{searchLessons (đã có – kiểm), updateLesson (sửa type), updateSentence, deleteSentence}`, `vocabularyApi.{deleteCard, updateCard (kiểm)}`, `subscriptionApi.{createActivationCodes, listActivationCodes}`; sửa mọi `any`.
4. `lib/queryKeys.ts`, `lib/constants.ts` (`CEFR_LEVELS`, `TOPIC_TAGS`+nhãn, `TOPIC_ICONS`), `lib/storageKeys.ts`, `lib/practice-session.ts`, `lib/practice-nav.ts` (`getNeighbors(sentences, id)`, `firstUndoneSentence(sentences, attempts, mode)`), thay `CEFR_LEVELS` lặp **chỉ trong file T06 sở hữu** (`register`); file khác do task Wave 2 tự thay.
5. Hooks: `useDebounce`, `useLocalStorage`, `useHotkeys` (quy tắc architecture §6.5), `useSentenceAudio` (sentence audio hoặc segment `startMs–endMs` của audio bài, auto-stop qua `timeupdate` + `requestAnimationFrame`, `playbackRate` từ localStorage, replay, đếm replay vào practice-session).
6. UI: `ErrorState`, `Pagination`, `Kbd`, `Modal` thêm `role="dialog" aria-modal`; `SentencePlayer` (Play/Pause, Replay, tốc độ 0.5–1.5), `PracticeHeader` ("Câu n/N", Prev/Next, "Hoàn thành bài", dùng `router.replace`), `ShortcutHelp`, `RecordingPlayer` (axios blob → objectURL, revoke khi unmount; hoặc nhận `blob` local).
7. NAV-02: `app/not-found.tsx` (404 tiếng Việt, link `/` và `/dashboard`), `app/error.tsx` + `app/(app)/error.tsx` (nút "Thử lại" = `reset()`).
8. Tooling: `.eslintrc.json` (`next/core-web-vitals`), bỏ `eslint.ignoreDuringBuilds`, thêm `vitest@1.6.0` devDependency + script `"test": "vitest run"`, `vitest.config.ts`; sửa lỗi lint hiện có **trong file T06 sở hữu**; lỗi lint ở file khác → chuyển thành warning tạm thời bằng override ESLint có liệt kê file và ghi Review notes (task Wave 2 xoá override khi sửa file đó).
9. Tests: `lib/api.refresh.test.ts` (3 request 401 song song → 1 lần refresh), `lib/practice-nav.test.ts`.

**Acceptance criteria**
- [ ] `tsc --noEmit`, `next lint`, `vitest run` xanh.
- [ ] Unit test: 3 lời gọi 401 đồng thời → hàm refresh được gọi đúng 1 lần, cả 3 được retry.
- [ ] Refresh thất bại → store trống, điều hướng `/login?next=…`.
- [ ] Mở `/vocabulary/review` khi chưa đăng nhập → `/login?next=%2Fvocabulary%2Freview`; đăng nhập xong quay lại đúng trang; `next=//evil.com` bị bỏ qua.
- [ ] URL không tồn tại → trang 404 tiếng Việt.
- [ ] `types.ts` không còn `any`; có đủ type/hàm của §4.5 (Reviewer đối chiếu bảng).
- [ ] Không file nào ngoài Owner files bị sửa (trừ override ESLint đã ghi chú).

**Verify**
```bash
scripts/verify-fe.sh   # hoặc lệnh Frontend ở architecture §1.1
```

**Status:** DONE-WITH-ISSUES

**Review notes:**
- Review Wave 1: `scripts/verify-fe.sh` green (tsc, `next lint` 0 warnings, vitest 25/25 incl. single-flight refresh test). Frontend container: `/`, `/login`, `/lessons` → 200; unknown route → 404 page. Static review of `src/lib` (api.ts refresh queue, `safeNextPath`, `uploadRecording` honouring `uploadRequiresAuth`) found no bugs.
- Not verified in a browser: refresh-failure redirect and `/login?next=` round-trip (unit tests only).
- Open (Low, process): `docs/reports/T06.md` is missing (T01–T05 have reports).
- Review Final: `docs/reports/T06.md` added (`d838bb5`). Browser E2E: invalid access token + valid refresh token → page still loads, exactly 1 `/auth/refresh-token` call, new token stored; non-admin `/admin/*` → `/dashboard`; no uncaught page errors. Open issues **resolved**.
- Open (Low, a11y): `FieldWrapper` (`components/ui/Input.tsx`) renders `<label>` without `htmlFor`/`id`, so form labels are not associated with their inputs (screen readers, `getByLabel`).

---

## T07 – Layout & điều hướng (desktop + mobile)

- **Branch:** `feat/T07-layout-nav`
- **Wave:** 2 · **Depends on:** T06
- **Spec items:** NAV-01, NAV-03, NAV-04, NAV-05, NAV-06 (shell), NAV-07, PRA-07 (Topbar dùng `/practice/streak`), ADM-01 (mục Sidebar admin gồm "Mã kích hoạt")
- **Owner files:** `components/layout/{AppShell,Sidebar,Topbar,PublicHeader}.tsx`, `components/layout/{MobileTabBar,MoreDrawer,navItems.ts,ReviewReminder}.tsx|ts` (mới), `app/(app)/layout.tsx`, `app/lessons/layout.tsx`, `app/pricing/layout.tsx` (mới), `app/page.tsx` (chỉ header nếu cần)

**Các bước**
1. `navItems.ts`: cấu trúc menu duy nhất (Dashboard · Bài học · Từ vựng [Bộ từ, Ôn tập (badge), Sổ tay] · Lịch sử · Premium · Hồ sơ · Admin [Bài học, Từ vựng, Mã kích hoạt]) dùng cho Sidebar, tab bar, drawer.
2. Sidebar: active theo prefix route con (NAV-03), thu gọn chỉ icon + lưu `parroto:sidebar-collapsed` (NAV-04), mục admin theo `profile.isAdmin`.
3. Topbar: streak từ `qk.streak`/`practiceApi.getStreak`, số thẻ cần ôn từ `qk.vocabStats`, badge PRO/nút "Nâng cấp", menu avatar (Hồ sơ, Đăng xuất).
4. NAV-01: `MobileTabBar` (< md: Dashboard, Bài học, Ôn tập, Lịch sử, Thêm) + `MoreDrawer`; ẩn tab bar trên route `/lessons/*/dictation/*` và `/lessons/*/shadowing/*`; padding đáy cho nội dung.
5. NAV-05: `app/lessons/layout.tsx` và `app/pricing/layout.tsx`: đã đăng nhập → `AppShell`, khách → `PublicHeader` (chờ hydrate, không nhấp nháy).
6. NAV-07: `ReviewReminder` trong `(app)/layout.tsx`: vào `/dashboard` khi `dueCount > 0`, tối đa 1 lần/ngày (`parroto:review-popup-date`).
7. NAV-06: shell không gây cuộn ngang ở 360px.

**Acceptance criteria**
- [ ] Viewport 375×812: mọi trang `(app)` tới được qua tab bar/drawer; không cuộn ngang ở 360px.
- [ ] Ở `/vocabulary/notebook`, "Từ vựng › Sổ tay" được highlight.
- [ ] Khách mở `/lessons` và `/pricing` thấy `PublicHeader`; đã đăng nhập thấy `AppShell`.
- [ ] Sidebar thu gọn giữ trạng thái sau reload.
- [ ] Topbar hiển thị streak từ `/practice/streak`; badge cần ôn giảm sau khi chấm thẻ (nhờ invalidate `qk.vocabStats` ở T10).
- [ ] Popup ôn tập chỉ hiện 1 lần/ngày.
- [ ] `tsc`, `lint`, `vitest` xanh.

**Verify**
```bash
scripts/verify-fe.sh
```

**Status:** DONE

**Review notes:**
- Review Final (Playwright, Chromium): dashboard Topbar streak chip (`Chuỗi N ngày…`), PRO badge after activation, avatar menu logout. 375×812: tab bar visible, "Thêm" opens the bottom-sheet drawer (Bộ từ, Sổ tay, Gói Premium, Hồ sơ, Đăng xuất); tab bar hidden on dictation. 360px: no horizontal scroll on /dashboard, /lessons, /lessons/{id}, /vocabulary, /vocabulary/notebook, /vocabulary/review, /practice/history, /profile, /subscription, /pricing. Not exercised in browser: sidebar collapse persistence, review popup once/day, guest PublicHeader (covered by unit tests).

---

## T08 – Lessons + Dictation + màn kết quả

- **Branch:** `feat/T08-lessons-dictation`
- **Wave:** 2 · **Depends on:** T06 (E2E: T03, T04)
- **Spec items:** LES-01..LES-06, DIC-01..DIC-06, DIC-08..DIC-10 (DIC-07 hiển thị), SHA-07 (màn kết quả `mode=shadowing`), NAV-06 (màn dictation)
- **Owner files:** `app/lessons/page.tsx`, `app/lessons/[id]/page.tsx`, `app/lessons/[id]/not-found.tsx` (mới), `app/(app)/lessons/[id]/dictation/**`, `app/(app)/lessons/[id]/result/**` (mới), `components/audio/AudioPlayer.tsx`, `components/practice/WordDiffView.tsx`, `components/lessons/**` (mới), `components/practice/dictation/**` (mới), `lib/dictation.ts` + `lib/dictation.test.ts` (mới)

**Các bước**
1. LES-01: mọi page của task dùng mẫu loading/error/notFound (architecture §6.3).
2. LES-02/03: `/lessons` search debounce 300ms (≥ 2 ký tự → `searchLessons`), chip CEFR + `TOPIC_TAGS`, state trong query string `?q=&level=&topic=&page=`, `Pagination`.
3. LES-04: nút "Luyện Dictation/Shadowing" → `firstUndoneSentence` (từ `getLessonAttempts`), khách → `/login?next=`.
4. LES-05: transcript toggle IPA/dịch (localStorage), ✓ + điểm tốt nhất mỗi câu. LES-06: `AudioPlayer` dùng `src` trực tiếp + `onError` trên `<audio>` → "Audio chưa khả dụng".
5. DIC-02/03/04: `PracticeHeader` + `SentencePlayer` (T06), `router.replace` khi chuyển câu, câu cuối → "Hoàn thành bài" → `/lessons/[id]/result?mode=dictation`.
6. DIC-06: `lib/dictation.ts`: `tokenize(text)` (giữ dấu câu ngoài ô), `blankMask(words, difficulty)` (Easy: điền sẵn vị trí chẵn; Normal: điền sẵn 0,4,8…; Hard: textarea), `composeUserInput(words, mask, typed, revealed)` (từ lộ → chuỗi rỗng); UI ô từ (Space → ô kế, Backspace ô rỗng → ô trước); chế độ lưu `parroto:dictation-difficulty`.
7. DIC-05: `useHotkeys` theo bảng spec 3.3; `ShortcutHelp`.
8. DIC-08: gợi ý 1 từ (click ô/Ctrl+H), "Hiện tất cả", "Bỏ qua câu"; đếm submits/replays/hints/ms vào `practice-session`.
9. DIC-09: "Chính xác!" (accuracy = 100) + âm/rung (`navigator.vibrate` nếu có), ngược lại "Chưa đúng, thử lại"; hiện IPA + dịch sau nộp; invalidate `practiceStats`, `streak`, `lessonAttempts`, `lessonStats`.
10. DIC-10 + SHA-07: `/lessons/[id]/result?mode=dictation|shadowing`: Final score (TB điểm tốt nhất/câu) + nhãn, câu đã làm/N (khớp `getLessonStats`), TB lần nộp/replay/từ lộ/câu + tổng thời gian (sessionStorage, nếu có), câu sai nhiều nhất (dictation) / top từ phát âm kém (shadowing, từ `wordScores` của attempts), nút "Làm lại", "Chuyển sang Shadowing/Dictation", "Bài khác" (cùng CEFR).
11. Tests `lib/dictation.test.ts`: câu 10 từ → Easy 5 ô, Normal 7 ô (điền sẵn 0/4/8), Hard textarea; deterministic; compose bỏ qua hoa/thường/dấu câu.
12. Thay `CEFR_LEVELS` lặp trong `app/lessons/page.tsx` bằng import.

**Acceptance criteria**
- [ ] Toàn bộ AC spec 3.2 (trừ AC admin thêm câu – thuộc T12/T04) và 3.3.
- [ ] `/lessons/<uuid không tồn tại>` → "Không tìm thấy bài học" ≤ 3s.
- [ ] Shift+→ ở câu 1 → câu 2; Back trình duyệt về trang trước bài.
- [ ] Mở modal, nhấn Enter → không nộp.
- [ ] Màn kết quả shadowing hiển thị điểm phát âm TB và top từ kém.
- [ ] `tsc`, `lint`, `vitest` xanh.

**Verify**
```bash
scripts/verify-fe.sh
```

**Status:** DONE

**Review notes:**
- Review Final (Playwright): /lessons level chip A1 → `?level=A1`, search "Greet" → `?q=Greet`; lesson detail → "Luyện Dictation" opens sentence 1. Normal: fill blanks + Enter → "🎉 Chính xác!" 80/80; switch to Hard via ⚙ → textarea + Enter → 100/100; `Shift+→` → sentence 2; wrong input → "Chưa đúng, thử lại"; "Bỏ qua & kết thúc" → `/result?mode=dictation` with final score and "Đã làm x/5 câu". Sentence audio loads from the FE origin after gateway fix `208b400`.
- Fixed by reviewer (Low) `b701cfc`: sentence IPA displayed as `//…//` (seed IPA already has slashes) in dictation, shadowing and transcript.

---

## T09 – Shadowing frontend (Recorder WAV)

- **Branch:** `feat/T09-shadowing-fe`
- **Wave:** 2 · **Depends on:** T06 (E2E: T01, T03)
- **Spec items:** SHA-01, SHA-02 (FE), SHA-03, SHA-05 (dùng `RecordingPlayer`), SHA-06, SHA-08, NAV-06 (màn shadowing)
- **Owner files:** `components/audio/Recorder.tsx`, `components/practice/PhonemeScoreView.tsx`, `components/practice/shadowing/**` (mới), `app/(app)/lessons/[id]/shadowing/**`, `lib/audio/**` (mới: `wav.ts`, `wav.test.ts`, `recorder-worklet.ts` hoặc `public/worklets/pcm-recorder.js`)

**Các bước**
1. SHA-01: Recorder = `getUserMedia({audio: {channelCount: 1, echoCancellation: true}})` + `AudioWorklet` (fallback `ScriptProcessorNode` cho Safari cũ) gom Float32 → resample về 16 kHz (`lib/audio/wav.ts`: `downsample(buffer, fromRate, 16000)`, `encodeWav(int16, 16000)`) → `Blob('audio/wav')`. Không dùng `MediaRecorder`.
2. SHA-03: tự dừng ở 30s (đồng hồ đếm), < 0.5s → báo "Bản ghi quá ngắn"; lỗi quyền micro (`NotAllowedError`) → panel hướng dẫn cấp quyền theo trình duyệt; giữ blob cuối cùng để "Gửi lại".
3. SHA-02: luồng `requestShadowingUploadUrl` → upload theo `uploadRequiresAuth` (architecture §6.1) → `submitShadowing`; lỗi upload/chấm → toast + nút "Gửi lại" dùng lại blob.
4. Màn shadowing: `PracticeHeader` + `SentencePlayer`, `Shift+\`` ghi/dừng, `Space` nghe lại bản ghi (blob local qua `RecordingPlayer`), "Đang chấm…", `PhonemeScoreView` (Đúng ≥ 80 / Gần đúng 60–79 / Thử lại < 60), badge "Chế độ demo – điểm mô phỏng" khi `scorer === 'mock'`; câu cuối → `/lessons/[id]/result?mode=shadowing`.
5. SHA-06: "Lần gần nhất" + "Trung bình" accuracy câu hiện tại từ `getLessonAttempts` (lọc mode).
6. SHA-08: ErrorState khi lỗi tải bài. Invalidate `practiceStats`, `streak`, `lessonAttempts`.
7. Test `lib/audio/wav.test.ts`: header RIFF/WAVE, sampleRate 16000, 1 kênh, 16 bit, độ dài data đúng; downsample 48k → 16k giữ đúng số mẫu (±1).

**Acceptance criteria**
- [ ] Chế độ mock (không key): ghi 3s trên Chrome/Firefox → upload 204 → nhận điểm từng từ + badge demo.
- [ ] File tải lên có header `RIFF…WAVE`, 16000 Hz, 1 kênh (kiểm bằng `xxd`/test).
- [ ] Từ chối micro → hướng dẫn, không crash; ghi > 30s tự dừng; < 0.5s báo lỗi.
- [ ] Mất mạng khi upload → toast + "Gửi lại" dùng lại bản ghi.
- [ ] Nghe lại bản ghi vừa thu bằng `Space`.
- [ ] `tsc`, `lint`, `vitest` xanh.

**Verify**
```bash
scripts/verify-fe.sh
```

**Status:** DONE

**Review notes:**
- Review Final (Playwright, `--use-fake-device-for-media-stream`): record 2.2 s → upload (local, 204) → per-word scores (5 words with %) + "🧪 Chế độ demo – điểm mô phỏng"; local take playback shown. History → shadowing row → detail modal `<audio src=blob:…>` plays.

---

## T10 – Vocabulary frontend (flashcard, review, notebook)

- **Branch:** `feat/T10-vocabulary-fe`
- **Wave:** 2 · **Depends on:** T06 (E2E: T05)
- **Spec items:** VOC-02, VOC-04, VOC-05, VOC-06, VOC-07, VOC-08 (FE), VOC-09, VOC-10
- **Owner files:** `app/(app)/vocabulary/**`, `components/practice/Flashcard.tsx`, `components/vocab/**` (mới), `lib/srs.ts` + `lib/srs.test.ts` (mới)

**Các bước**
1. `lib/srs.ts`: `SRS_RATINGS = [{key:'1', label:'Học lại', quality:1}, {key:'2', label:'Khó', quality:3}, {key:'3', label:'Tốt', quality:4}, {key:'4', label:'Dễ', quality:5}]` + `previewInterval(item, quality)` cùng công thức architecture §4.6; nhãn khoảng cách trên nút.
2. VOC-02: component `RatingButtons` + `useHotkeys` dùng chung cho `decks/[id]` và `review`: `Space`/`Enter` lật, `1–4` chấm khi đã lật (chưa lật → chỉ lật). Bỏ mảng 0/2/3/5 hard-code.
3. VOC-04: `decks/[id]` tải thẻ theo trang limit 50 + "Tải thêm"; trạng thái đã học từ `card.userVocabId`.
4. VOC-05: notebook debounce 300ms, `Pagination` limit 20, hiện `nextReviewAt`, interval, xác nhận xoá (Modal).
5. VOC-06: thanh tiến độ phiên ôn, tổng kết (số thẻ, phân bố 4 mức), "Không còn thẻ đến hạn – lần ôn kế tiếp lúc …".
6. VOC-07: Flashcard hiển thị đủ field, phát âm `audioUrl` hoặc `speechSynthesis` (en-US).
7. VOC-08: 403 deck Pro → card nâng cấp → `/subscription`.
8. VOC-09: chế độ gõ từ (toggle): đúng → gợi ý Good, sai → Again. VOC-10: giới hạn thẻ/ngày (1–1000, mặc định 100) ở `parroto:review-daily-limit`, cắt phiên.
9. Sau mỗi lần chấm invalidate `qk.vocabStats`, `qk.reviewQueue`. Thay `TOPIC_ICONS`/`CEFR_LEVELS` lặp bằng import `lib/constants`.
10. Test `lib/srs.test.ts`: ví dụ §4.6 (Again 1, Hard/Good/Easy 25; n=1 Good → 6).

**Acceptance criteria**
- [ ] Phím `3` khi đã lật → request `quality=4`; khi chưa lật → chỉ lật.
- [ ] Deck 150 thẻ → xem được thẻ thứ 150.
- [ ] Notebook gõ "app" nhanh → 1 request; phân trang khi > 20 từ.
- [ ] Badge "cần ôn" ở Sidebar/Topbar giảm ngay sau mỗi lần chấm.
- [ ] Nhãn khoảng cách trên nút khớp `interval` BE trả về.
- [ ] `tsc`, `lint`, `vitest` xanh.

**Verify**
```bash
scripts/verify-fe.sh
```

**Status:** DONE

**Review notes:**
- Review Final (Playwright): deck page `Space` flips, keys 1–4 rate (4 cards added); review (cards made due by SQL) → flip with Space, rate with 1–4, summary with Again/Hard/Good/Easy distribution and next review time; notebook shows "Ôn tiếp: …" and "Chu kỳ N ngày" (BE fields from T05 FIX-R1).

---

## T11 – Dashboard, lịch sử luyện tập, hồ sơ

- **Branch:** `feat/T11-dashboard-history-profile`
- **Wave:** 2 · **Depends on:** T06 (E2E: T02, T03)
- **Spec items:** PRA-01, PRA-02, PRA-03 (FE), PRA-04, PRA-05, PRA-06, PRA-07 (Dashboard), AUTH-06, AUTH-07 (FE), AUTH-08, SHA-05 (dùng trong history)
- **Owner files:** `app/(app)/dashboard/**`, `app/(app)/practice/history/**`, `app/(app)/profile/**`, `components/dashboard/**` (mới), `components/history/**` (mới)

**Các bước**
1. PRA-01: thẻ Streak hiện tại/dài nhất, tổng giờ, accuracy Dictation/Shadowing, bài hoàn thành, số từ đã học, thẻ cần ôn (link), "Tiếp tục học" (bài của attempt gần nhất, `listAttempts({limit:1})`).
2. PRA-02: `WeeklyAccuracyChart` SVG thuần từ `accuracy.weekly/dates`. PRA-03: `ActivityHeatmap` 90 ngày (lưới 13×7, màu theo attempts, `title` tooltip). PRA-04: `TrendingErrors` top 10.
3. PRA-05: History bảng: tên bài (`useQueries` theo `lessonId` duy nhất, key `qk.lesson(id)` để chia sẻ cache), ngày giờ `createdAt` (định dạng `vi-VN`, giờ VN), mode, độ khó, điểm; lọc mode; link "Luyện lại" về câu tương ứng; `Pagination`.
4. PRA-06: modal chi tiết: `WordDiffView` (dictation) / `PhonemeScoreView` + `RecordingPlayer` theo `attemptId` (shadowing).
5. AUTH-06: Profile số giờ từ `getStats().totalHoursLearned`, số từ từ `vocabularyApi.getStats()`. AUTH-07: ẩn form đổi mật khẩu khi `authProvider !== 'local'`. AUTH-08: ErrorState thay spinner vô hạn. Thay `CEFR_LEVELS` lặp bằng import.

**Acceptance criteria**
- [ ] Biểu đồ tuần có đúng `accuracy.dates.length` điểm; heatmap 90 ô.
- [ ] History hiển thị tên bài + ngày giờ mọi dòng; 20 dòng / 3 bài → ≤ 3 request `GET /content/lessons/{id}`.
- [ ] Modal lượt shadowing phát được bản ghi (qua blob, có Bearer).
- [ ] Profile số giờ/từ khớp Dashboard; tài khoản Google không thấy form đổi mật khẩu.
- [ ] Tắt practice-service → Dashboard/Profile hiện ErrorState có "Thử lại", không spinner vô hạn.
- [ ] `tsc`, `lint`, `vitest` xanh.

**Verify**
```bash
scripts/verify-fe.sh
```

**Status:** DONE

**Review notes:**
- Review Final (Playwright): new user dashboard renders the weekly SVG chart and exactly 90 heatmap cells without errors; after practice: KPI tiles, "Tiếp tục học", trending errors. History table with lesson names, VN date-time, mode/difficulty, detail modal.

---

## T12 – Subscription, pricing & Admin frontend

- **Branch:** `feat/T12-subscription-admin-fe`
- **Wave:** 2 · **Depends on:** T06 (E2E: T04, T05)
- **Spec items:** SUB-03, SUB-06, SUB-07, ADM-01, ADM-02, ADM-03 (FE), ADM-04 (FE), ADM-05 (FE), ADM-06 (FE), ADM-09
- **Owner files:** `app/pricing/page.tsx`, `app/subscription/**`, `app/(app)/subscription/**`, `app/(app)/admin/**` (gồm `admin/layout.tsx`, `admin/vocabulary/[deckId]/**`, `admin/activation-codes/**` mới), `components/admin/**` (mới), `components/subscription/**` (mới)

**Các bước**
1. SUB-06: `/pricing` nội dung (layout do T07): bảng so sánh Free vs Premium theo `plan.features`, giá định dạng `79.000đ`; nút mua chưa đăng nhập → `/login?next=/subscription`.
2. SUB-07: bỏ mọi chỗ dùng `autoRenew`; ghi "Thanh toán 1 lần, không tự gia hạn".
3. SUB-03: `/subscription/success` poll `getUserSubscription` mỗi 2s tối đa 10s → "Đã kích hoạt đến <ngày>" hoặc "Đang xử lý…"; invalidate `userSubscription`, `profile`. Kích hoạt mã: hiện lỗi BE nguyên văn.
4. ADM-01: `admin/layout.tsx` kiểm `profile.isAdmin` (chờ load; không phải admin → `router.replace('/dashboard')` + toast "Không có quyền").
5. ADM-02: form sửa bài (`title, description, topicTag (TOPIC_TAGS), cefrLevel`) gọi `updateLesson`. ADM-09: ErrorState trang `admin/lessons/[id]`.
6. ADM-03: bảng câu sửa inline / modal (text, ipa, translationVi, startMs, endMs, sentenceOrder) + xoá có xác nhận. ADM-04: chặn file không `audio/*` hoặc > 50MB ở client.
7. ADM-05: `/admin/vocabulary/[deckId]`: danh sách thẻ phân trang + search, thêm/sửa/xoá đủ field (gồm `partOfSpeech`, `exampleVi`, `audioUrl`, `imageUrl`); link từ `/admin/vocabulary`.
8. ADM-06: `/admin/activation-codes`: form tạo lô (plan, số lượng ≤ 500, hạn), danh sách lọc đã dùng/chưa, copy từng mã / copy tất cả, export CSV (client-side Blob).
9. Thay `CEFR_LEVELS` lặp trong admin bằng import.

**Acceptance criteria**
- [ ] User thường mở `/admin/lessons` → về `/dashboard`, không thấy UI admin.
- [ ] Sửa tiêu đề bài → `/lessons` hiện tiêu đề mới ngay; sửa/xoá câu hoạt động.
- [ ] Upload mp3 20MB thành công; `.exe` bị chặn ở client (và 400 nếu bỏ qua client).
- [ ] Tạo 10 mã gói 1 tháng → hiện 10 mã, export CSV có 10 dòng + header.
- [ ] Kích hoạt mã → `/subscription` hiện hạn mới; `/subscription/success` hiện trạng thái thật.
- [ ] `/pricing` hiện 3 gói 79k/169k/499k, không có `autoRenew`.
- [ ] `tsc`, `lint`, `vitest` xanh.

**Verify**
```bash
scripts/verify-fe.sh
```

**Status:** DONE

**Review notes:**
- Review Final (Playwright): /pricing shows 79.000đ / 169.000đ / 499.000đ; /subscription activates `PROMO-DEMO1-MONTH` → Topbar PRO + status card, reuse → "Mã đã được sử dụng"; admin edits a (throw-away) lesson title → persisted; "Tạo mã" shows new `PROMO-XXXXX-XXXXX` codes; non-admin `/admin/lessons` → `/dashboard`.

---

## T13 – Người dùng tự thêm từ vựng vào sổ tay (VOC-NB-ADD)

**Yêu cầu (người dùng, 2026-10-13):** người dùng tự thêm từ của mình vào Sổ tay; từ tự thêm được ôn bằng SM-2 như thẻ khác.
Chia thành **T13-BE** (nhánh `feat/T13-be-personal-words`) và **T13-FE** (nhánh `feat/T13-fe-add-word`), chạy song song theo hợp đồng dưới.

### Thiết kế
- `vocab_deck` thêm cột `owner_id UUID NULL` (+ index). `owner_id IS NULL` = deck công khai (admin); `owner_id = userId` = **deck cá nhân ẩn**, tên `"Sổ tay cá nhân"`, `is_pro_only=false`, tạo lười (lazy) khi user thêm từ đầu tiên, tối đa 1 deck/user.
- Từ tự thêm = một `vocab_card` trong deck cá nhân + một `user_vocab` (next_review_at = now → vào review queue ngay).
- Deck cá nhân **không** xuất hiện ở `GET /vocabs/decks`; `GET /vocabs/decks/{id}/cards` của deck người khác → 404; admin endpoints không đụng tới deck cá nhân.
- Migration: sửa `CREATE TABLE vocab_deck` trong `init-db.sql`, và thêm `scripts/migrations/001_personal_vocab.sql` (idempotent: `ALTER TABLE vocab_deck ADD COLUMN IF NOT EXISTS owner_id UUID; CREATE UNIQUE INDEX IF NOT EXISTS ux_vocab_deck_owner ON vocab_deck(owner_id) WHERE owner_id IS NOT NULL;`) để áp lên DB đang chạy.

### Hợp đồng API (vocabulary-service, qua gateway `/vocabs/...`, cần Bearer)
| Method | Path | Body | Kết quả |
|---|---|---|---|
| POST | `/vocabs/user/words` | `CreatePersonalWordRequest` | 201 `LearnedWordResponse` |
| PUT | `/vocabs/user/words/{userVocabId}` | `CreatePersonalWordRequest` | 200 `LearnedWordResponse` |
| DELETE | `/vocabs/user/{userVocabId}` (đã có) | – | 200; nếu là từ tự thêm thì xoá luôn `vocab_card` và giảm `card_count` |

`CreatePersonalWordRequest`: `word` (bắt buộc, trim, 1–100 ký tự), `meaning` (bắt buộc, 1–500), `ipa?` (≤100), `partOfSpeech?` (≤50), `example?` (≤500), `exampleVi?` (≤500). Chuỗi rỗng → null.

`LearnedWordResponse` thêm: `custom: boolean` (true nếu card thuộc deck cá nhân của user), `ipa`, `partOfSpeech`, `example`, `exampleVi` (nullable).

Lỗi (error JSON chuẩn, thông điệp tiếng Việt):
- Từ đã có trong sổ tay (so khớp `lower(trim(word))` với mọi từ user đã có, kể cả từ từ deck) → **409** `"Từ này đã có trong sổ tay"` (PUT: chỉ 409 nếu trùng với từ *khác*).
- PUT/xoá `userVocabId` không thuộc user → 404 `"Không tìm thấy từ"`; PUT một từ không phải tự thêm → **403** `"Chỉ sửa được từ bạn tự thêm"`.
- Validation → 400.
- Giới hạn: tối đa **2000** từ tự thêm/user → 400 `"Sổ tay cá nhân đã đạt giới hạn 2000 từ"`.
- Sau khi tạo/sửa/xoá: xoá cache liên quan (vocab user stats).

### T13-BE – acceptance
- [ ] POST tạo từ → 201, xuất hiện ở `/vocabs/user/learned` với `custom=true`, và ở `/vocabs/user/review-queue` ngay.
- [ ] POST lần 2 cùng từ (khác hoa/thường, có khoảng trắng) → 409. Từ đã học từ deck công khai → 409.
- [ ] PUT sửa nghĩa → 200; PUT từ của deck công khai → 403; PUT/DELETE từ của user khác → 404.
- [ ] DELETE từ tự thêm → xoá card; `GET /vocabs/decks` không bao giờ trả deck cá nhân; cards của deck cá nhân người khác → 404.
- [ ] Unit test service (tạo deck lười, trùng từ, quyền sửa, giới hạn); `mvn test` xanh.

### T13-FE – acceptance
- [ ] `types.ts`/`endpoints/vocabulary.ts`/`queryKeys.ts`: thêm trong khối `// --- T13 additions ---` (`CreatePersonalWordInput`, `LearnedWord` thêm các field trên, `vocabularyApi.createPersonalWord/updatePersonalWord`).
- [ ] Component `components/vocab/AddWordModal.tsx` (tạo + sửa, có prefill; Enter submit; hiện lỗi backend `error`), unit test cho hàm chuẩn hoá form.
- [ ] Trang `/vocabulary/notebook`: nút "+ Thêm từ"; badge "Tự thêm" cho `custom`; nút sửa (chỉ `custom`); hiển thị IPA/ví dụ nếu có; empty state gợi ý thêm từ.
- [ ] Dictation (sau khi nộp câu): bấm một từ trong phần đáp án → mở AddWordModal prefill `word` = từ đó (bỏ dấu câu), `example` = câu gốc, `exampleVi` = bản dịch câu; toast "Đã thêm vào sổ tay".
- [ ] Thành công → invalidate `learnedWordsAll`, `vocabStats`, `reviewQueue`.
- [ ] tsc / lint / vitest xanh.

**Status:** DONE
**Review notes:** (orchestrator, 2026-10-13) BE `cb113e5` (mvn 45/45) + FE `5bb0931` (vitest 145/145, build OK) merged into dev/multi-agent. Migration applied to the running DB. API E2E via gateway 12/12 (create 201 custom=true, in learned + review queue, case/space duplicate 409, validation 400, PUT 200, other user PUT/DELETE 404, personal deck hidden from /vocabs/decks, no token 401, delete removes word). Browser E2E `e2e/tests/t13-notebook-add.spec.ts` 2/2 (notebook add/duplicate/edit; dictation click-word → prefilled modal → saved). Doc fix: `docker-compose exec -T` (snap) does not forward stdin, so the migration command now uses `docker exec -i`.
**Review notes:**

## T14 – Khu quản trị riêng cho admin (Admin console)

**Yêu cầu (người dùng, 2026-10-13):** admin không dùng giao diện học như user; admin chỉ có khu quản trị CRUD. Thêm quản lý **Người dùng**, **Gói Premium & giao dịch**, **sửa thông tin deck** (ngoài Bài học / Từ vựng / Mã kích hoạt đã có).
Chia: **T14-AUTH** `feat/T14-auth-admin-users`, **T14-SUB** `feat/T14-sub-admin-plans`, **T14-VOC** `feat/T14-voc-deck-update`, **T14-FE** `feat/T14-fe-admin-console` — chạy song song theo hợp đồng dưới.

Quy ước chung: mọi endpoint admin cần Bearer token **và** email thuộc `ADMIN_EMAILS` (server-side, như các AdminGuard hiện có) → không phải admin: **403** `"Bạn không có quyền quản trị"`. Error JSON chuẩn, thông điệp tiếng Việt. Phân trang theo `PageResponse` hiện có (`data`, `page`, `limit`, `total`, `totalPages` — giữ đúng tên field của service đó). Tất cả path đi qua gateway hiện có (`/auth/**`, `/subscriptions/**`, `/vocabs/decks/**` đã được proxy; không sửa gateway).

### T14-AUTH (auth-service)
- `AdminGuard` cho auth-service (dùng `admin.emails`).
- `GET /auth/admin/users?q=&status=all|active|locked&page=1&limit=20` → `PageResponse<AdminUserResponse>`; `q` tìm theo email (ILIKE), sắp xếp `created_at DESC`.
  `AdminUserResponse`: `id`, `email`, `authProvider`, `cefrLevel`, `isActive`, `isAdmin`, `createdAt`, `lastLoginAt`.
- `GET /auth/admin/users/{id}` → `AdminUserResponse` (404 `"Không tìm thấy người dùng"`).
- `PATCH /auth/admin/users/{id}` body `{ "isActive": boolean }` → `AdminUserResponse`. Không cho khoá chính mình hoặc tài khoản admin khác → 400 `"Không thể khoá tài khoản quản trị"`.
  Khi khoá: login (đã có kiểm tra is_active) và **refresh-token** đều trả 403 `"Tài khoản đã bị khoá"`; access token cũ hết hạn tự nhiên (≤ 1 giờ) — ghi rõ trong report.
- `GET /auth/admin/stats` → `{ totalUsers, activeUsers, lockedUsers, newUsersLast7Days, newUsersLast30Days }` (không tính tài khoản admin).
- Unit test: guard, search/filter, khoá/mở khoá, chặn khoá admin, refresh bị chặn khi khoá.

### T14-SUB (subscription-service + DB)
- Migration `scripts/migrations/003_plan_active.sql` (idempotent): `ALTER TABLE plan ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT true; ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP`; sửa `CREATE TABLE plan` trong `init-db.sql` tương ứng. Entity `Plan` thêm `isActive`, `updatedAt`.
- Public `GET /subscriptions/plans` chỉ trả plan `is_active = true` (giữ nguyên shape cũ). `create-payment`/kích hoạt mã với plan bị tắt → 400 `"Gói này hiện không còn bán"`.
- `GET /subscriptions/admin/plans` → `AdminPlanResponse[]` (cả gói tắt): `id`, `name`, `durationDays`, `price`, `features` (string[]), `isActive`, `activeSubscribers`, `createdAt`, `updatedAt`.
- `POST /subscriptions/admin/plans` body `{ id, name, durationDays, price, features: string[], isActive }` → 201. `id` dạng `^[a-z0-9-]{3,50}$`, trùng → 409 `"Mã gói đã tồn tại"`. `price` ≥ 1000, `durationDays` 1–3650, `name` 1–255.
- `PUT /subscriptions/admin/plans/{id}` cùng body trừ `id` → 200. **Không xoá cứng** plan (đã có subscription/giao dịch tham chiếu) — tắt bằng `isActive=false`.
- `GET /subscriptions/admin/transactions?status=all|pending|success|failed&method=&q=&from=&to=&page=&limit=` → `PageResponse<AdminTransactionResponse>`: `id`, `userId`, `planId`, `planName`, `amount`, `method`, `status`, `transactionId`, `description`, `createdAt`. `q` khớp `transactionId` hoặc `userId`. `from/to` là ngày `YYYY-MM-DD` (giờ VN).
- `GET /subscriptions/admin/stats` → `{ activeSubscribers, revenueTotal, revenueLast30Days, successfulTransactions, pendingTransactions, revenueByDay: [{date, amount}] (30 ngày gần nhất, đủ 30 phần tử) }` — doanh thu = tổng `amount` giao dịch `success`, không tính `gift_code`.
- `POST /subscriptions/admin/user-status` body `{ userIds: UUID[] }` (≤ 100) → `[{ userId, premium: boolean, planId, endAt }]` — để trang Người dùng hiện cột Premium.
- Xoá cache `premium:*`/plans liên quan sau khi ghi. Unit test cho validation, lọc plan tắt, thống kê doanh thu.

### T14-VOC (vocabulary-service)
- `PUT /vocabs/decks/{id}` (admin) body `{ name, description, level (A1–C2|null), isProOnly }` → 200 `DeckResponse`. Deck cá nhân (owner_id ≠ null) → 404. Validation: `name` 1–255. Xoá cache `vocab:decks:*`.
- Unit test.

### T14-FE (frontend)
- **Tách khu admin**: route group mới `app/(admin)/admin/**` với `AdminShell` riêng (sidebar quản trị: Tổng quan, Người dùng, Bài học, Từ vựng, Gói & giao dịch, Mã kích hoạt; topbar chỉ có tên admin + Đăng xuất; mobile drawer). Di chuyển các trang admin hiện có từ `app/(app)/admin/**` sang, **không** render AppShell học tập (không streak, ôn tập, nâng cấp, review reminder).
- **Điều hướng theo vai trò**: đăng nhập xong → admin vào `/admin`, user vào `/dashboard`. Admin mở trang học (`(app)` group: dashboard, lessons practice, vocabulary, history, subscription, profile học) → chuyển về `/admin`. User mở `/admin/**` → `/dashboard` + toast (đã có). Trang public `/lessons`, `/pricing` khi admin đăng nhập → `/admin`. Admin vẫn có trang **Tài khoản** `/admin/account` (đổi mật khẩu) thay cho Hồ sơ học tập.
- **Trang mới**: `/admin` (Tổng quan: thẻ số liệu từ `/auth/admin/stats`, `/subscriptions/admin/stats`, tổng bài học & deck từ các list hiện có; biểu đồ doanh thu 30 ngày bằng SVG thuần), `/admin/users` (tìm kiếm, lọc trạng thái, phân trang, cột Premium từ `user-status`, nút Khoá/Mở khoá có xác nhận), `/admin/plans` (bảng gói: sửa giá/tên/thời hạn/quyền lợi, bật/tắt, tạo gói mới), `/admin/transactions` (lọc trạng thái/phương thức/ngày, tìm mã GD, phân trang), deck: form **Sửa deck** trong `/admin/vocabulary` và `/admin/vocabulary/[deckId]`.
- types/endpoints: thêm trong khối `// --- T14 additions ---`; dùng `qk` mới trong khối T14 của `queryKeys.ts`.
- Kiểm tra: tsc / lint / vitest xanh; unit test cho hàm định dạng/doanh thu.

**Status:** DONE
**Review notes:** (orchestrator, 2026-10-13) Merged T14-AUTH `4015fd0` (mvn 51/51), T14-SUB `e9746ed` (mvn 50/50, migration 003 applied to the running DB), T14-VOC `1f9e7c2` (mvn 50/50), T14-FE `037ad94` (vitest 173/173, build OK). Paged admin responses use the existing nested `{data, pagination}` shape; plan/user-status validation answers 422 (Bean Validation) — FE shows the backend text for 400/422. API smoke via gateway OK (stats, users, plans, transactions, deck PUT, non-admin 403). Browser E2E `e2e/tests/admin-console.spec.ts` 2/2 (admin lands on /admin with no learner UI; /dashboard, /vocabulary, /lessons redirect to /admin; lock → login 403 "Tài khoản đã bị khoá" → unlock; admin not lockable; plan off hides it from /subscriptions/plans, on again; transactions page; non-admin /admin → /dashboard). Learner regression E2E 4/4. Known: access tokens issued before a lock stay valid until expiry (≤ 1 h).
**Review notes:**

## T15 – Trò chơi "Dịch nhanh" (Việt → Anh) — Giai đoạn 1

**Yêu cầu (người dùng, 2026-10-14):** random nghĩa tiếng Việt, người chơi gõ từ tiếng Anh; có cơ chế chơi và tính điểm khiến người chơi muốn chơi tiếp. Đề xuất 3 giai đoạn đã được đồng ý; T15 = **Giai đoạn 1**. Nhánh `feat/T15-translate-game`.
**Status:** DONE

### Phạm vi
- Chế độ **Nước rút 60 giây** (`SPRINT`), nguồn từ: **Bộ từ** (deck công khai hoặc deck Pro nếu Premium), **Theo trình độ** (A1–C2, deck công khai), **Từ của tôi** (sổ tay, từ đến hạn trước).
- Chấm đáp án (`GameAnswerChecker`): bỏ qua hoa/thường, khoảng trắng, dấu câu cuối, phần trong ngoặc, "to " ở đầu; "colour/color" nhận cả hai; **từ khác có nghĩa trùng** (một đoạn nghĩa tách bởi , ; /) trong deck mà người chơi thấy được → đúng; **sai 1 ký tự** (thêm/bớt/thay/đảo 2 chữ, từ ≥ 4 chữ) → gần đúng.
- Tính điểm phía server (`GameScoring`): `round(gốc × combo × gợi ý) + thưởng tốc độ`; gốc A1–A2 10, B1 15, B2 20, C1–C2 30; combo (tính cả câu hiện tại) 5+ ×1.5, 10+ ×2, 20+ ×3; gợi ý 1/2/3 lần ×0.8/0.5/0.25; tốc độ < 3 s +5, < 6 s +2 (chỉ khi không gợi ý); gần đúng = nửa điểm, giữ combo; sai/bỏ qua = 0, combo về 0. Thời gian đo bằng giờ server; trả lời trễ ≤ 2 s sau khi hết giờ vẫn được chấm.
- Gợi ý 3 bậc: từ loại + mặt nạ `_ _ _ _` → chữ cái đầu mỗi từ → câu ví dụ có chỗ trống (không có ví dụ thì lộ thêm chữ cuối).
- Từ sai/bỏ qua được hỏi lại 1 lần sau 5 câu. Kết thúc: tổng kết, kỷ lục cá nhân theo (chế độ, nguồn), danh sách từ cần ôn + "Thêm tất cả vào sổ tay".
- Lịch ôn SM-2: từ đã có trong sổ tay, lần trả lời đầu tiên của mỗi từ: sai/bỏ qua → Again (luôn áp dụng); gần đúng hoặc dùng gợi ý → Hard, đúng sạch → Good (chỉ áp dụng khi từ **đã đến hạn**, để chơi không đẩy lịch ôn ra xa).
- Ván bỏ dở: khi bắt đầu ván mới, ván cũ đã có câu trả lời được chốt (FINISHED), chưa trả lời câu nào thì ABANDONED.

### API (vocabulary-service, gateway `/vocabs/games` cần token)
- `POST /vocabs/games/sessions` `{source: DECK|NOTEBOOK|LEVEL, deckId?, level?}` → 201 `GameState` (câu hỏi đầu, `remainingMs`, `previousBest`). Không lộ từ tiếng Anh trước khi trả lời.
- `POST /vocabs/games/sessions/{id}/hint` → `Hint`; `POST .../answer` `{answer}` hoặc `{skip:true}` → `AnswerResult` (kết quả, điểm, combo, đáp án, câu kế tiếp, `finished`, `timeUp`); `POST .../finish` → `Summary` (idempotent); `GET /vocabs/games/me` → số ván, tổng điểm, kỷ lục theo nguồn.
- Lỗi: 400 nguồn/trình độ sai, sổ tay trống, đáp án rỗng; 403 deck Pro; 404 ván của người khác; 409 ván đã kết thúc.
- DB: `game_session`, `game_answer` (`scripts/migrations/004_vocab_game.sql`, cũng có trong `init-db.sql`).

### Frontend
- `/games` (menu "⚡ Dịch nhanh"): sảnh chọn nguồn + cách tính điểm + kỷ lục → màn chơi (đồng hồ, điểm, combo, gợi ý, bỏ qua, phản hồi đáp án + phát âm) → tổng kết (Chơi lại / Đổi nguồn từ).
- Phím tắt tuỳ chỉnh nhóm "Trò chơi Dịch nhanh": Gợi ý `Ctrl+H`, Bỏ qua `Tab`; Enter giữ cho nộp đáp án.

### Kiểm thử
- vocabulary-service 71 test (mới: `GameAnswerCheckerTest` 6, `GameScoringTest` 6, `GameServiceTest` 9); frontend 181 test (mới `lib/game.test.ts` 8); tsc + lint sạch.
- E2E `e2e/tests/translate-game.spec.ts` 2/2 (ván đầy đủ với combo/gợi ý/bỏ qua/tổng kết/sổ tay/chơi lại/nguồn sổ tay; ván tự kết thúc khi hết 60 s); hồi quy admin-console, custom-shortcuts, deck-typing-retry, t13 6/6.

### Giới hạn đã biết
- Danh sách thẻ của deck công khai vẫn xem được qua API, nên người cố tình tra đáp án có thể gian lận điểm — chấp nhận ở Giai đoạn 1 vì chưa có bảng xếp hạng (cần xử lý trước Giai đoạn 2).
- Nghĩa trùng chỉ so khớp nguyên đoạn nghĩa; từ đồng nghĩa không có trong hệ thống bị tính sai.
- Giai đoạn 2 (Sinh tồn, Thử thách hằng ngày, bảng xếp hạng) và 3 (XP/cấp, huy hiệu, mục tiêu ngày) chưa làm.

## Bảng đối chiếu spec → task (kiểm tra phủ 82 mục)

| Module | Mục → Task |
|---|---|
| X | X-01 T01 · X-02 T02 (+T05 client, T01 env) · X-03 T02 (+T01 compose) · X-04 T01 · X-05 T01 (Java), T02 (GW) · X-06 T02 · X-07 T01 (CI), T06 (FE), T02/T03/T05 (JUnit) · X-08 T06 |
| AUTH | 01 T06 · 02 T02 · 03 T02 · 04 T02 (+T06 FE) · 05 T06 · 06 T11 · 07 T02 (BE) + T11 (FE) · 08 T11 |
| LES | 01 T08 (+ ErrorState T06) · 02–06 T08 · 07 T04 · 08 T01 + T04 |
| DIC | 01–06, 08–10 T08 · 07 T03 |
| SHA | 01, 03, 06, 08 T09 · 02 T03 + T09 · 04 T03 · 05 T06 (+ dùng ở T09, T11) · 07 T08 |
| VOC | 01, 03 T05 · 02, 04–10 T10 (04, 07 có phần BE ở T05; 08 có phần BE SUB-04 ở T05) |
| PRA | 01, 02, 04, 05, 06 T11 (05 BE ở T03) · 03 T03 (BE) + T11 (FE) · 07 T03 (BE) + T07/T11 (FE) |
| SUB | 01, 02, 04, 05 T05 (02 route GW ở T02) · 03, 06, 07 T12 |
| ADM | 01 T12 (+ Sidebar T07) · 02 T12 (+T04, type T06) · 03, 04 T04 + T12 · 05, 06 T05 + T12 · 07 T05 · 08 T01 · 09 T12 |
| NAV | 01, 03, 04, 05, 07 T07 · 02 T06 · 06 T07 + T08 + T09 |

---

## Phase 2+ Backlog

Không thuộc phạm vi các task trên. Danh sách đầy đủ và ưu tiên gợi ý: [`docs/spec.md` §5 Backlog Phase 2+](./spec.md#5-backlog-phase-2-chưa-có-trong-dự-án). Tiền đề bắt buộc trước mọi hạng mục cần bảng/cột mới: thêm **Flyway** cho 5 service (nhóm 0), sau đó (theo spec) Ôn câu sai + bài Pro → Gamification core → sổ từ cá nhân + từ điển → quên mật khẩu → các nhóm còn lại. Gói **Lifetime** (1.849.000đ) cũng thuộc Phase 2 (nhóm 8).

---

## Review Wave 1

> 2026-10-13, Reviewer/Tester on `dev/multi-agent` (HEAD 03ef1ae + fix 3350240). Stack: `docker-compose down -v && docker-compose up -d --build` with the user's `.env` (+ appended `INTERNAL_TOKEN`, `REDIS_URL`, `REDIS_TIMEOUT`, `STORAGE_PROVIDER`, `SPEECH_PROVIDER`, `MEDIA_ROOT`; no AWS/Azure keys → local storage + mock scorer). E2E through the gateway `:8000` with a Python smoke script (104 checks). Full log: `docs/reports/review-wave1.md`.

**Totals:** 100 PASS · 2 FAIL (Medium 1, Low 1) · 1 N/A · 1 blocker FIXED (3350240).

| ID | Check | Result | Detail |
|---|---|---|---|
| AUTH-REG | register user A | PASS | 201 |
| AUTH-REG-B | register user B | PASS | 201 |
| AUTH-ADMIN | register/login admin@parroto.com | PASS | 200 |
| AUTH-LOGIN | login user A | PASS | 200 |
| AUTH-PROFILE | GET /auth/profile | PASS | 200 |
| AUTH-02-GW | refresh token as Bearer on /practice/stats (gateway) -> 401 | PASS | 401 |
| AUTH-02-SVC | refresh token as Bearer direct to practice-service -> 401 | PASS | 401 |
| AUTH-NOTOKEN | no token /practice/stats -> 401 | PASS | 401 |
| AUTH-04 | login-google with blank GOOGLE_CLIENT_ID -> 503 | N/A | user .env has a `your_…` placeholder, so the service treats Google as configured and answers 401 "Invalid Google token"; blank-id → 503 path checked by code review + unit test only |
| X-02-AUTH-NOTOKEN | auth /internal/users/<id> without token -> 401 | PASS | 401 |
| X-02-AUTH-TOKEN | auth /internal/users/<id> with token -> 200 | PASS | 200 |
| X-02-SUB-NOTOKEN | subscription /internal/... without token -> 401 | PASS | 401 |
| X-02-SUB-WRONG | subscription /internal/... wrong token -> 401 | PASS | 401 |
| X-02-GW | gateway does not expose /internal (404) | PASS | 404 |
| SUB-PLANS | plans = 79000/169000/499000, no lifetime | PASS | 200 [79000, 169000, 499000] |
| LES-LIST | GET /content/lessons | PASS | 200 n=5 |
| LES-DETAIL | GET /content/lessons/{id} with sentences | PASS | 200 n=5 |
| LES-08-AUDIO | seed lesson audio 200 audio/mpeg | PASS | 200 audio/mpeg 81547 |
| LES-RANGE | audio with Range -> 206 + Content-Range | PASS | 206 bytes 0-99/81547 100 |
| LES-SENT-AUDIO | seed sentence audio 200 | PASS | 200 audio/mpeg |
| DIC-07-Easy | dictation perfect Easy -> 60 | PASS | 201 score=60.0 |
| DIC-07-Normal | dictation perfect Normal -> 80 | PASS | 201 score=80.0 |
| DIC-07-Hard | dictation perfect Hard -> 100 | PASS | 201 score=100.0 |
| SHA-URL | request-upload-url storage=local uploadRequiresAuth=true | PASS | 200 {uploadUrl: /practice/shadowing/recordings/c63f4534-398c-4b30-b581-d4ce07b3fcdf, … |
| SHA-PUT-WEBM | PUT non-WAV -> 400 | PASS | 400 {"error":"\xc4\x90\xe1\xbb\x8bnh d\xe1\xba\xa1ng ghi \xc3\xa2m kh\xc3\xb4ng h\xe1\xb… |
| SHA-PUT-48K | PUT 48 kHz WAV -> 400 | PASS | 400 {"error":"\xc4\x90\xe1\xbb\x8bnh d\xe1\xba\xa1ng ghi \xc3\xa2m kh\xc3\xb4ng h\xe1\xb… |
| SHA-PUT-NOAUTH | PUT without Bearer -> 401 | PASS | 401 |
| SHA-PUT | PUT 16k mono WAV with Bearer -> 204 | PASS | 204  |
| SHA-SUBMIT | submit shadowing -> scorer=mock with word scores | PASS | 201 scorer=mock score=98.29 |
| SHA-OWNER | owner streams recording -> 200 audio/wav | PASS | 200 audio/wav |
| SHA-04 | other user's recording -> 404 | PASS | 404 |
| PRA-03-90 | activity?days=90 -> 90 entries | PASS | 200 n=90 |
| PRA-03-TODAY | today's activity counts the attempts | PASS | {date: 2026-10-13, attempts: 4, minutes: 0} |
| PRA-03-400 | activity?days=400 -> 400 | PASS | 400 |
| PRA-05 | GET /practice/attempts (no lessonId) 200 + difficulty | PASS | 200 [{attemptId: 8e04ab34-49ca-4457-9048-dc85aa948fd6, lessonId: 11111111-1111-1111… |
| PRA-STATS | GET /practice/stats 200 | PASS | 200 |
| PRA-07 | GET /practice/streak 200, current>=1 | PASS | 200 {"currentStreak":1,"lastPracticeDate":"2026-10-13T03:29:35.23608Z"} |
| VOC-DECKS | GET /vocabs/decks | PASS | 200 n=3 |
| VOC-CARDS | GET deck cards | PASS | 200 n=8 |
| VOC-PRO-LIST | Pro deck cards listable (preview) or 403 | PASS | 403 |
| VOC-ADD | add 4 cards to notebook | PASS | {0: ca910950-35d9-435b-b630-46ce4260233e, 1: 15a63e39-0d85-46c3-b86f-90ceabe82d19, 2: … |
| VOC-04 | deck cards with Bearer expose userVocabId for learned cards | PASS | n=4 |
| VOC-01-RATINGS | review ratings 1/3/4/5 all 200 | PASS | {1: 200, 3: 200, 4: 200, 5: 200} |
| VOC-01-N1 | second Good review -> interval 6 | PASS | {userVocabId: ec554875-58dc-4e81-9ed3-40386680f2c6, nextReviewAt: 2026-10-19T03:29:… |
| VOC-01-AGAIN | Again resets -> interval 1, repetitions 0 | PASS | {userVocabId: ec554875-58dc-4e81-9ed3-40386680f2c6, nextReviewAt: 2026-10-14T03:29:… |
| VOC-07 | review-queue 200 with new fields | PASS | {queue: [], totalDueCards: 0, totalCards: 4} |
| VOC-STATS | vocabs/user/stats 200 | PASS | 200 |
| SUB-05-ACT | activate demo code -> premium | PASS | 200 {"subscription":{"id":"3022545f-51e6-4a88-871b-0fac41730be6","planId":"plan-1month",… |
| SUB-USER | subscriptions/user isPremium | PASS | 200 |
| SUB-05-REUSE | reuse code -> 400 'Mã đã được sử dụng' | PASS | 400 {"error":"M\xc3\xa3 \xc4\x91\xc3\xa3 \xc4\x91\xc6\xb0\xe1\xbb\xa3c s\xe1\xbb\xad d\x… |
| SUB-05-UNKNOWN | unknown code -> 404 | PASS | 404 |
| SUB-05-ACCUM | second code accumulates (daysRemaining ~ 30+90) | PASS | 200 119 |
| ADM-06-403 | normal user create activation codes -> 403 | PASS | 403 |
| ADM-06-403-LIST | normal user list activation codes -> 403 | PASS | 403 |
| ADM-03-403-PUT | normal user PUT sentence -> 403 | PASS | 403 |
| ADM-03-403-DEL | normal user DELETE sentence -> 403 | PASS | 403 |
| ADM-05-403 | normal user DELETE card -> 403 | PASS | 403 |
| ADM-07-403 | normal user POST deck -> 403 | PASS | 403 |
| ADM-LES-403 | normal user POST lesson -> 403 | PASS | 403 |
| ADM-06-CREATE | admin creates 10 unique codes | PASS | 201 {codes: [{id: 4c55b213-5277-43c3-866a-948ed4a8704a, code: PROMO-ECA9W-MDKNW,… |
| ADM-06-LIST | list used=true shows usedByEmail | PASS | 200 [{id: d1a6b833-d09a-491e-bd38-deec99b906f4, code: PROMO-DEMO1-MONTH, planId:… |
| ADM-LES-CREATE | admin creates lesson | PASS | 201 {"id":"c5b314cd-9acb-4e3f-8c09-d56f299391cc","title":"RW1 test 1791862171","sourceUr… |
| ADM-SENT-ADD | admin adds 5 sentences | PASS | 201 [{"id":"41bf2d1c-490f-4da6-ac75-38a45e369534","sentenceOrder":1,"text":"Sentence 0",… |
| LES-07-COUNT | list sentenceCount=5 right after add (cache evicted) | PASS | [5] |
| ADM-03-PUT | admin PUT sentence 200 | PASS | 200 {"id":"41bf2d1c-490f-4da6-ac75-38a45e369534","sentenceOrder":1,"text":"Sentence 0","… |
| LES-07-PUT | detail shows new endMs immediately | PASS | [{id: 41bf2d1c-490f-4da6-ac75-38a45e369534, sentenceOrder: 1, text: Sentence 0, … |
| ADM-03-VALID | startMs>=endMs -> 400 | PASS | 400 |
| ADM-03-DEL | delete sentence 2/5 -> orders 1..4 | PASS | 200 [1, 2, 3, 4] |
| LES-07-COUNT-DEL | list sentenceCount=4 after delete | PASS | [4] |
| ADM-04-20MB | admin upload 20MB mp3 -> 200 | PASS | 200 {"audioUrl":"local:lessons/c5b314cd-9acb-4e3f-8c09-d56f299391cc/970801be-8f92-43e5-b… |
| ADM-04-PLAY | uploaded audio streams with Range 206 | PASS | 206 bytes 0-1023/20971520 |
| ADM-04-EXE | upload .exe -> 400 | PASS | 400 {"error":"File ph\xe1\xba\xa3i l\xc3\xa0 audio (mp3, m4a, wav, ogg, webm)","status":… |
| ADM-LES-DEL | admin deletes test lesson | PASS | 200 |
| ADM-07 | admin creates deck | PASS | 201 {"id":"a58a430b-9270-4273-83d1-093e31132831","name":"RW1 deck 1791862171","descripti… |
| ADM-05 | delete card -> card_count 3->2 | PASS | card_count 3→2 (verified manually via GET /vocabs/decks; script read a non-existent GET /v… |
| SUB-PAY | free user create-payment | PASS | 200 {transactionId: 140c46c3-1fcc-40fc-9840-f84c8abd95bf, paymentUrl: https://sandb… |
| SUB-02-97 | IPN bad signature -> 97 | PASS | tatus=00&vnp_TxnRef=140c46c3-1fcc-40fc-9840-f84c8abd95bf&vnp_SecureHash=0dc42f1ade1ae96747… |
| SUB-02-04 | IPN wrong amount -> 04 | PASS | tatus=00&vnp_TxnRef=140c46c3-1fcc-40fc-9840-f84c8abd95bf&vnp_SecureHash=d595581db559d8e278… |
| SUB-02-04-NOSUB | after wrong amount B still not premium | PASS | {"subscription":null,"isPremium":false} |
| SUB-02-00 | IPN valid -> 00 | PASS | tatus=00&vnp_TxnRef=2210de1c-cb6f-441c-a16d-51622a9b5b6e&vnp_SecureHash=d108dd23b70cfd7541… |
| SUB-02-02 | IPN replay -> 02 | PASS | tatus=00&vnp_TxnRef=2210de1c-cb6f-441c-a16d-51622a9b5b6e&vnp_SecureHash=25b0e9b82b62c20f8a… |
| SUB-02-PREM | B premium after IPN | PASS | {"subscription":{"id":"d6541ab8-bc42-4984-ad4d-0adbbcb06cc2","planId":"plan-1month","pla… |
| SUB-02-01 | IPN unknown txn -> 01 | PASS | tatus=00&vnp_TxnRef=445e8593-75cf-4045-b37f-7c8cf5050c61&vnp_SecureHash=d966258dd796742d9a… |
| AUTH-03-LOGOUT | logout 200 | PASS | 200 {"message":"Logged out successfully"} |
| AUTH-03-practice-stats | old token after logout /practice/stats -> 401 | PASS | 401 |
| AUTH-03-vocabs-user-stats | old token after logout /vocabs/user/stats -> 401 | PASS | 401 |
| AUTH-03-subscriptions-user | old token after logout /subscriptions/user -> 401 | PASS | 401 |
| AUTH-03-auth-profile | old token after logout /auth/profile -> 401 | PASS | 401 |
| AUTH-03-SVC | old token direct to vocabulary-service -> 401 | PASS | 401 |
| FE- | frontend / -> 200 | PASS | 200 |
| FE-login | frontend /login -> 200 | PASS | 200 |
| FE-lessons | frontend /lessons -> 200 | PASS | 200 |
| FE-404 | frontend unknown route -> 404 page | PASS | 404 |
| T01-CONFIG | `docker-compose config -q` with updated .env | PASS | |
| T01-PORTS | only host ports 3000/8000 published | PASS | |
| T01-UP | `down -v` + `up -d --build`, fresh volume with .env DB passwords → 8 services healthy, frontend up | PASS | |
| AUTH-JWT-ALG | (run 1) access tokens accepted by gateway | FIXED | every token rejected 401: auth signed HS512 (68-char JWT_SECRET) but GW accepts only HS256 → fixed in 3350240 |
| SUB-04-EVICT | free user Pro card 403 → `premium:{id}` cached → activate code → key deleted → add Pro card 201 | PASS | |
| VOC-07-FIELDS | review-queue item has exampleVi/partOfSpeech/audioUrl/imageUrl | PASS | |
| X-03-GW | gateway image with empty / short JWT_SECRET exits 1 with FATAL log | PASS | |
| X-06 | no `.keys(` in services/gateway sources | PASS | |
| T06-VERIFY | `scripts/verify-fe.sh`: tsc, next lint (0 warnings), vitest 25/25 | PASS | |
| AUTH-03-REFRESH | refresh token after logout rejected | FAIL | not an AC; see T02 notes (Medium) |
| ERR-UUID | malformed UUID / unmapped route in content & vocabulary → 4xx | FAIL | `GET /content/lessons/abc`, `GET /vocabs/decks/xyz/cards`, `GET /vocabs/decks/{id}` → 500 (Low) |

---

## Review Final

> 2026-10-13, Reviewer/Tester (final round) on `dev/multi-agent` (start `d838bb5`). `docker-compose down -v && up -d --build` (all 8 back-end containers healthy). Note: the user's `.env` has `LOG_LEVEL=DEBUG` (left as is). Full log: `docs/reports/review-final.md`.

**Totals:** API regression 46/46 PASS + VNPay IPN 4/4 PASS; browser E2E (Playwright 1.47.2, Chromium, `e2e/`) 10/10 PASS after fixes. 1 High + 1 Low bug found and fixed (`208b400`, `b701cfc`); 2 Low open.

| Area | Check | Result | Detail |
|---|---|---|---|
| API | Wave 1 smoke (auth, dictation 60/80/100, shadowing mock, stats, vocab SM-2, Pro gating, codes, plans, FE routes) | PASS | 46/46, `docs/reports/review-final.md` §2 |
| API | FIX-R1 logout `{refreshToken}` → refresh 401 | PASS | 401; logout without body 200 |
| API | FIX-R1 `/content/lessons/abc` → 400, `/vocabs/decks/xyz/cards` → 400, unknown route → 404 | PASS | no 500s |
| API | FIX-R1 `/vocabs/user/learned` has `nextReviewAt/intervalDays/easeFactor` | PASS | |
| API | FIX-R1 Google placeholder client id → 503 | PASS | |
| API | VNPay IPN 97 / 04 / 00 / 02 | PASS | `scripts/vnpay-ipn-test.sh` |
| E2E 01 | register → dashboard, weekly chart, 90-cell heatmap, Topbar streak | PASS | |
| E2E 02 | /lessons search + level filter, open lesson | PASS | |
| E2E 03 | dictation Normal + Hard, Enter submit, score, `Shift+→`, finish → result | PASS | after `208b400` (audio) and `b701cfc` (IPA) |
| E2E 04 | shadowing fake mic 2 s → word scores + demo badge; history blob playback | PASS | |
| E2E 05 | deck study keys Space/1–4; review flip + 1–4; notebook schedule | PASS | cards made due via SQL |
| E2E 06 | pricing 79.000đ/169.000đ/499.000đ; demo code → PRO; reuse → error | PASS | |
| E2E 07 | admin edit lesson title, create codes; non-admin → /dashboard | PASS | |
| E2E 08 | mobile 375×812 tab bar + drawer; 360px no horizontal scroll (10 pages) | PASS | |
| E2E 09 | invalid access token + refresh token → page works, 1 refresh call; UI logout revokes refresh | PASS | |
| E2E | uncaught page errors / console errors | PASS | 0 pageerrors; only expected network logs (400 reused code, 401 before refresh) |
| Bug | Gateway CORP `same-origin` blocked all lesson/sentence audio in the browser | FIXED (High) | `208b400` |
| Bug | Sentence IPA shown as `//…//` | FIXED (Low) | `b701cfc` |
| Open | `HEAD` on content audio → 401 | Low | T04 notes |
| Open | Form `<label>` not associated with inputs | Low (a11y) | T06 notes |

