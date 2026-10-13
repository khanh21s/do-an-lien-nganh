# Phân tích hiện trạng dự án Parroto (clone)

> Ngày phân tích: 2026-10-13. Phạm vi: `frontend/`, `api-gateway/`, `services/*`, `init-db.sql`, `docker-compose.yml`, `files/` (tài liệu kế hoạch gốc).
> Trạng thái git lúc phân tích: nhánh `main`, HEAD = `1e2bac5 "WIP: frontend AppShell layout, lessons/deck page and flashcard updates"`. Lúc bắt đầu phân tích, các file dưới đây **chưa commit**, sau đó đã được commit vào commit WIP này. Vẫn coi chúng là **WIP**:
> `docker-compose.yml` (cổng Postgres cấu hình được), `frontend/src/app/(app)/layout.tsx`, `(app)/vocabulary/decks/[id]/page.tsx`, `app/lessons/page.tsx`, `app/lessons/[id]/page.tsx`, `app/lessons/layout.tsx` (mới), `components/layout/AppShell.tsx` (mới), `Sidebar.tsx`, `Topbar.tsx`, `practice/Flashcard.tsx`.

---

## 1. Kiến trúc & stack thực tế

```
Browser ──► Next.js frontend :3000 ──(axios, NEXT_PUBLIC_API_URL)──► API Gateway (Express) :8000
                                                                        │ verify JWT (HS256, shared secret), rate-limit, proxy
     ┌──────────────┬──────────────┬───────────────┬───────────────────┬┘
 auth :8001     content :8002   practice :8003   vocabulary :8004   subscription :8005
 auth_db        content_db      practice_db      vocabulary_db      subscription_db     (1 Postgres 16, DB-per-service)
                 S3 (audio)      S3 + Azure Speech  ─► subscription /internal  ─► auth /internal, VNPay, SMTP
                                 ─► content (REST)
                         Redis 7 (cache + blacklist JWT) dùng chung
```

| Thành phần | Công nghệ / phiên bản (từ manifest) | Cổng |
|---|---|---|
| Frontend | Next.js **14.2.35** (App Router, `output: 'standalone'`), React 18.3.1, TypeScript 5.5.3, Tailwind 3.4.6, @tanstack/react-query 5.51.1, axios 1.7.2, zustand 4.5.4 (persist), react-hot-toast 2.4.1, clsx 2.1.1, @react-oauth/google 0.12.1. Đã pin phiên bản chính xác + có `package-lock.json` | 3000 |
| API Gateway | Node ≥20, express ^4.19.2, http-proxy-middleware ^2.0.6, jsonwebtoken ^9.0.2, express-rate-limit ^7.4.0, helmet ^7.1.0, cors, morgan, uuid. Dùng dải `^`, **không có lockfile** | 8000 |
| 5 service | Spring Boot **3.2.1** (parent), `java.version` 21, Spring Web/Data JPA/Redis/Security/Validation/Actuator, PostgreSQL driver 42.7.1, jjwt 0.12.3, Lombok 1.18.48, AWS SDK v2 2.25.3 (content, practice). `ddl-auto: validate` (schema chỉ đến từ `init-db.sql`) | 8001–8005 |
| Docker build Java | `maven:3.9-eclipse-temurin-25` → `eclipse-temurin:25-jre-alpine` (JDK 25 trong khi pom nhắm Java 21) | – |
| Hạ tầng | `postgres:16-alpine` (cổng host `${POSTGRES_HOST_PORT:-5432}`, mới đổi, WIP), `redis:7-alpine` (6379, appendonly, 512MB LRU) | 5432/6379 |

**Các service trong docker-compose:** postgres, redis, auth-service, content-service, practice-service (phụ thuộc content healthy), vocabulary-service, subscription-service, api-gateway, frontend. Mọi service đều publish cổng ra host (8001–8005, 5432, 6379). Healthcheck dùng `/actuator/health` và `/health`.

**Xác thực:** auth-service phát access JWT (3600s) + refresh JWT (604800s), claim `sub, email, cefrLevel, type, jti`. Gateway và từng service tự validate bằng `JWT_SECRET` dùng chung. Quyền admin = danh sách `ADMIN_EMAILS` (không có bảng role). Premium = vocabulary-service gọi `GET subscription/internal/subscriptions/{userId}/active` (cache Redis 5 phút).

---

## 2. Màn hình frontend (`frontend/src/app`)

Quy ước: mọi page là `'use client'`, lấy dữ liệu bằng react-query, lỗi hiện bằng `toast` + `apiErrorMessage`. `(app)` = route group có `AuthGuard` + `AppShell` (sidebar + topbar).

| Route | File | Chức năng | API gọi | Trạng thái |
|---|---|---|---|---|
| `/` | `app/page.tsx` | Landing page, 4 thẻ tính năng, CTA | – | Hoàn chỉnh (tĩnh) |
| `/login` | `app/login/page.tsx` | Đăng nhập email/mật khẩu + nút Google (ẩn nếu chưa có client id) | `POST /auth/login`, `POST /auth/login-google` | Hoàn chỉnh. Không có Apple login, không có "quên mật khẩu" |
| `/register` | `app/register/page.tsx` | Đăng ký + chọn CEFR | `POST /auth/register` | Hoàn chỉnh |
| `/pricing` | `app/pricing/page.tsx` | Bảng giá công khai | `GET /subscriptions/plans` | Hoàn chỉnh. Không dùng `PublicHeader` như các trang public khác |
| `/subscription/success` | `app/subscription/success/page.tsx` | Trang kết quả VNPay thành công | – | Hoàn chỉnh (tĩnh, không xác minh giao dịch) |
| `/subscription/failed` | `app/subscription/failed/page.tsx` | Trang VNPay thất bại, map `?reason=` | – | Hoàn chỉnh |
| (layout) `/lessons/*` | `app/lessons/layout.tsx` **(mới, WIP)** | Đã đăng nhập → `AppShell`, chưa → `PublicHeader` | – | WIP |
| `/lessons` | `app/lessons/page.tsx` **(WIP)** | Danh sách bài học, lọc CEFR, phân trang | `GET /content/lessons` | Chạy được. Không dùng `searchLessons`, không lọc `topicTag` |
| `/lessons/[id]` | `app/lessons/[id]/page.tsx` **(WIP)** | Chi tiết bài + audio + danh sách câu, nút Dictation/Shadowing | `GET /content/lessons/{id}`, `<audio>` → `/content/lessons/{id}/audio`, `/content/sentences/{id}/audio` | **Lỗi**: lesson 404/lỗi mạng → spinner vô hạn (`if (isLoading \|\| !lesson)`, dòng 20) |
| (layout) `(app)` | `app/(app)/layout.tsx` **(WIP)** | `AuthGuard` + `AppShell` | – | WIP |
| `/dashboard` | `(app)/dashboard/page.tsx` | 8 thẻ thống kê + quick link | `GET /practice/stats`, `GET /vocabs/user/stats` | Hoàn chỉnh. Không dùng `accuracy.weekly` (biểu đồ xu hướng) |
| `/lessons/[id]/dictation/[sentenceId]` | `(app)/lessons/[id]/dictation/[sentenceId]/page.tsx` | Nghe và gõ lại, chọn độ khó, xem diff từng từ | `GET /content/lessons/{id}`, `POST /practice/dictation/attempt` | Hoàn chỉnh. Cùng lỗi spinner vô hạn (dòng 32). Không có điều hướng câu trước/sau |
| `/lessons/[id]/shadowing/[sentenceId]` | `(app)/lessons/[id]/shadowing/[sentenceId]/page.tsx` | Ghi âm, upload S3 presigned, chấm phát âm | `POST /practice/shadowing/request-upload-url`, PUT S3, `POST /practice/shadowing/attempt` | **Lỗi nặng**: luồng ghi âm/upload/chấm điểm không thể chạy (xem §7, B1–B2) |
| `/practice/history` | `(app)/practice/history/page.tsx` | Lịch sử lượt luyện + modal chi tiết | `GET /practice/attempts`, `GET /practice/attempts/{id}` | Chạy được. Không hiện tên bài/ngày; phát lại recording bị hỏng (B3) |
| `/vocabulary` | `(app)/vocabulary/page.tsx` | Lưới bộ từ vựng (icon/tint theo index) | `GET /vocabs/decks?limit=50` | Hoàn chỉnh |
| `/vocabulary/decks/[id]` | `(app)/vocabulary/decks/[id]/page.tsx` **(WIP)** | Danh sách chủ đề + flashcard lật 3D + chấm SM-2 (tự thêm vào sổ tay) | `GET /vocabs/decks`, `GET /vocabs/decks/{id}/cards?limit=100`, `GET /vocabs/user/learned?limit=500`, `POST /vocabs/user/add-from-lesson`, `POST /vocabs/user/review` | Chạy được. Giới hạn cứng 100 thẻ/500 từ; lỗi 403 premium hiện card nâng cấp |
| `/vocabulary/review` | `(app)/vocabulary/review/page.tsx` | Phiên ôn SRS | `GET /vocabs/user/review-queue`, `POST /vocabs/user/review` | Hoàn chỉnh |
| `/vocabulary/notebook` | `(app)/vocabulary/notebook/page.tsx` | Sổ tay, tìm kiếm, xoá | `GET /vocabs/user/learned`, `DELETE /vocabs/user/{id}` | Chạy được. Tìm kiếm không debounce (gọi API mỗi phím), không phân trang (limit 100) |
| `/subscription` | `(app)/subscription/page.tsx` | Gói hiện tại, mua qua VNPay, nhập mã kích hoạt, lịch sử giao dịch | `GET /subscriptions/user`, `/plans`, `/transactions`, `POST /create-payment`, `POST /activate-code` | Hoàn chỉnh (VNPay cần cấu hình thật) |
| `/profile` | `(app)/profile/page.tsx` | Xem hồ sơ, đổi CEFR, đổi mật khẩu, xoá tài khoản | `GET/PUT /auth/profile`, `PUT /auth/change-password`, `DELETE /auth/account` | Hoàn chỉnh. Không sửa avatar; form đổi mật khẩu vẫn hiện cho tài khoản Google |
| `/admin/lessons` | `(app)/admin/lessons/page.tsx` | Danh sách, tạo, xoá bài học | `GET/POST/DELETE /content/lessons` | Chạy được. Không sửa bài (`updateLesson` không dùng), không có guard `isAdmin` phía client |
| `/admin/lessons/[id]` | `(app)/admin/lessons/[id]/page.tsx` | Upload audio bài, thêm câu | `GET /content/lessons/{id}`, `POST .../upload-audio`, `POST .../sentences` | Chạy được. Không sửa/xoá câu (backend cũng không có API này); giới hạn upload 5MB |
| `/admin/vocabulary` | `(app)/admin/vocabulary/page.tsx` | Tạo/xoá deck, thêm thẻ | `GET/POST/DELETE /vocabs/decks`, `POST /vocabs/decks/{id}/cards` | Chạy được. Không xem/sửa thẻ (`updateCard` không dùng), không nhập `exampleVi/partOfSpeech/audio/image` |

Thiếu so với một clone Parroto đầy đủ: không có trang 404/error (`not-found.tsx`, `error.tsx`), không có điều hướng mobile (sidebar `hidden md:flex`), không có trang quản lý mã kích hoạt / người dùng, không có trang thống kê theo bài.

---

## 3. Danh sách API

Gateway (`api-gateway/server.js`) mount theo tiền tố; "GW auth" là kiểm tra tại gateway, "Svc auth" là kiểm tra tại service.

### 3.1 Bảng định tuyến gateway

| Tiền tố gateway | Middleware | Đích | Rewrite |
|---|---|---|---|
| `/health` | – | gateway trả trực tiếp | – |
| `/auth/register`, `/login`, `/login-google`, `/login-apple`, `/refresh-token` | ipLimiter | auth:8001 | giữ nguyên |
| `/auth/*` (còn lại) | `verifyToken`, userLimiter | auth | giữ nguyên |
| `/content/*` | `requireAuthForWrites` (GET public) | content:8002 | **bỏ `/content`** |
| `/practice/*` | `verifyToken`, userLimiter | practice:8003 | giữ nguyên |
| `/vocabs/decks/*` | `requireAuthForWrites` | vocabulary:8004 | giữ nguyên |
| `/vocabs/cards/*` | `verifyToken` (không có userLimiter) | vocabulary | giữ nguyên |
| `/vocabs/user/*` | `verifyToken`, userLimiter | vocabulary | giữ nguyên |
| `/subscriptions/plans`, `/subscriptions/payment-callback` | – | subscription:8005 | giữ nguyên |
| `/subscriptions/*` | `verifyToken`, userLimiter | subscription | giữ nguyên |
| `/internal/*` | **không định tuyến** (đúng thiết kế) | – | – |

### 3.2 Endpoint theo service

Ký hiệu: **P** = public, **A** = cần JWT, **AD** = admin (`AdminGuard`/`ADMIN_EMAILS`), **PR** = premium, **I** = internal (permitAll, không có xác thực).

**auth-service** (`AuthController`, `ProfileController`, `InternalUserController`)

| Method | Path (service) | Quyền | Qua gateway | FE dùng |
|---|---|---|---|---|
| POST | `/auth/register` | P | `/auth/register` | ✔ |
| POST | `/auth/login` | P | ✔ | ✔ |
| POST | `/auth/login-google` | P | ✔ | ✔ |
| POST | `/auth/login-apple` | P | ✔ | khai báo, không có UI |
| POST | `/auth/refresh-token` | P | ✔ | khai báo, **không dùng** |
| POST | `/auth/logout` | A (đưa jti vào blacklist) | ✔ | ✔ |
| GET/PUT | `/auth/profile` | A | ✔ | ✔ |
| PUT | `/auth/change-password` | A | ✔ | ✔ |
| DELETE | `/auth/account` | A | ✔ | ✔ |
| GET | `/internal/users/{id}` | I | – | – |

**content-service** (`LessonController`, `AudioController`)

| Method | Path (service) | Quyền | Gateway | FE dùng |
|---|---|---|---|---|
| GET | `/lessons?cefrLevel&topicTag&page&limit` | P | `/content/lessons` | ✔ |
| GET | `/lessons/search?q` | P | `/content/lessons/search` | khai báo, **không dùng** |
| GET | `/lessons/{id}` | P | ✔ | ✔ |
| POST | `/lessons` | AD | ✔ | ✔ |
| POST | `/lessons/{id}/sentences` | AD | ✔ | ✔ |
| PUT | `/lessons/{id}` | AD | ✔ | khai báo, **không dùng** |
| DELETE | `/lessons/{id}` (soft delete) | AD | ✔ | ✔ |
| GET | `/lessons/{id}/audio` (stream S3, Range) | P | ✔ | ✔ (`<audio>`) |
| GET | `/lessons/{id}/audio/download` | P | ✔ | ✘ |
| POST | `/lessons/{id}/upload-audio` (multipart, ≤5MB) | AD | ✔ | ✔ |
| GET | `/sentences/{id}/audio` | P | `/content/sentences/{id}/audio` | ✔ |

**practice-service** (`DictationController`, `ShadowingController`, `AttemptController`, `StatsController`), tất cả đều A

| Method | Path | FE dùng |
|---|---|---|
| POST | `/practice/dictation/attempt` | ✔ |
| POST | `/practice/shadowing/request-upload-url` | ✔ |
| POST | `/practice/shadowing/attempt` | ✔ |
| GET | `/practice/shadowing/{attemptId}/recording` | ✘ (và **không kiểm tra chủ sở hữu**, xem S3) |
| GET | `/practice/attempts?lessonId&mode&page&limit` | ✔ |
| GET | `/practice/attempts/{id}` | ✔ |
| GET | `/practice/lessons/{lessonId}/attempts` | ✘ |
| GET | `/practice/stats` | ✔ |
| GET | `/practice/stats/by-lesson/{lessonId}` | ✘ |
| GET | `/practice/stats/trending-errors` | ✘ |
| GET | `/practice/streak` | ✘ |

**vocabulary-service** (`DeckController`, `CardController`, `ReviewController`)

| Method | Path | Quyền | FE dùng |
|---|---|---|---|
| GET | `/vocabs/decks?level&isProOnly&page&limit` | P | ✔ |
| GET | `/vocabs/decks/{id}/cards?searchWord&page&limit` | P; deck `isProOnly` → **PR** (403 nếu không premium/ẩn danh) | ✔ (không dùng `searchWord`) |
| POST | `/vocabs/decks` | AD | ✔ |
| POST | `/vocabs/decks/{id}/cards` | AD | ✔ |
| DELETE | `/vocabs/decks/{id}` | AD | ✔ |
| PUT | `/vocabs/cards/{id}` | AD | khai báo, **không dùng** |
| GET | `/vocabs/user/review-queue` | A | ✔ |
| POST | `/vocabs/user/review` | A | ✔ |
| GET | `/vocabs/user/stats` | A | ✔ |
| GET | `/vocabs/user/learned?searchWord&page&limit` | A | ✔ |
| POST | `/vocabs/user/add-from-lesson` (body `{cardId, source}`) | A (**không kiểm tra premium** của thẻ) | ✔ |
| DELETE | `/vocabs/user/{userVocabId}` | A | ✔ |

**subscription-service** (`SubscriptionController`, `InternalSubscriptionController`)

| Method | Path | Quyền | FE dùng |
|---|---|---|---|
| GET | `/subscriptions/plans` | P | ✔ |
| GET | `/subscriptions/user` | A | ✔ |
| POST | `/subscriptions/create-payment` | A (503 nếu chưa cấu hình VNPay) | ✔ |
| GET | `/subscriptions/payment-callback` | P (VNPay return URL → redirect FE) | (trình duyệt) |
| POST | `/subscriptions/activate-code` | A | ✔ |
| GET | `/subscriptions/transactions` | A | ✔ |
| GET | `/internal/subscriptions/{userId}/active` | I | – |

### 3.3 Đối chiếu FE ↔ BE

- **FE gọi mà BE không có route:** *không có*. Mọi hàm trong `frontend/src/lib/endpoints/*.ts` đều khớp method + path + tên tham số với một route của gateway/service.
- **BE có mà FE không gọi:** `/auth/refresh-token`, `/auth/login-apple` (không có UI), `/content/lessons/search`, `PUT /content/lessons/{id}`, `/content/lessons/{id}/audio/download`, `PUT /vocabs/cards/{id}`, `/practice/lessons/{id}/attempts`, `/practice/stats/by-lesson/{id}`, `/practice/stats/trending-errors`, `/practice/streak`, `/practice/shadowing/{id}/recording`.
- **Có trong spec (`files/parroto-backend-prompts.md`) nhưng chưa làm:** `GET /health/deep`, `POST /practice/calculate-streak`, `POST /vocabs/user/add-deck`; endpoint Actuator `prometheus` được expose nhưng thiếu dependency `micrometer-registry-prometheus`.
- **Lệch nhỏ về payload:** `contentApi.updateLesson` gửi `Partial<CreateLessonInput>` (gồm `audioUrl/sourceUrl/duration`), nhưng `UpdateLessonRequest` chỉ nhận `title, description, topicTag, cefrLevel`. FE `CardInput` thiếu `partOfSpeech` (BE có).

---

## 4. Mô hình dữ liệu

| DB | Bảng | Cột chính | Entity JPA | Ghi chú |
|---|---|---|---|---|
| auth_db | `"user"` | id UUID, email UNIQUE, password_hash, auth_provider, cefr_level, total_hours_learned, total_words_learned, avatar_url, is_active, last_login_at, created/updated_at | `User` | Không có cột role/is_admin (admin dựa trên env). `total_hours_learned/total_words_learned` không được service nào khác cập nhật (dữ liệu nằm ở practice/vocab DB) nên Profile luôn hiện 0 |
| content_db | `lesson` | id, title, source_url, cefr_level, topic_tag, **audio_url NOT NULL**, description, duration, is_deleted | `Lesson` | Seed 5 bài (audio trỏ tới S3 bucket chưa tồn tại) |
| content_db | `sentence` | id, lesson_id FK CASCADE, sentence_order, text, ipa, translation_vi, start_ms, end_ms, audio_url, image_url | `Sentence` (ManyToOne Lesson) | Seed câu cho 5 bài |
| practice_db | `practice_attempt` | id, user_id, lesson_id, sentence_id, mode, score, accuracy, word_scores JSONB, audio_recording_url, duration, difficulty, created_at | `PracticeAttempt` (`JsonNode` + `@JdbcTypeCode(JSON)`) | Tham chiếu chéo DB chỉ là UUID, không có FK. `audio_recording_url` thực chất lưu **S3 key**, không phải URL |
| vocabulary_db | `vocab_deck` | id, name, description, level, is_pro_only, card_count (denormalized), created_by | `VocabDeck` | Seed 3 deck (1 deck Pro). `created_by` không bao giờ được gán |
| vocabulary_db | `vocab_card` | id, deck_id FK CASCADE, word, meaning, example, example_vi, ipa, audio_url, image_url, part_of_speech | `VocabCard` | Seed 24 thẻ |
| vocabulary_db | `user_vocab` | id, user_id, card_id FK CASCADE, next_review_at, interval_days, ease_factor, repetitions, quality, last_review_at | `UserVocab` | UNIQUE (user_id, card_id) |
| subscription_db | `plan` | id VARCHAR, name, duration_days, price BIGINT (VND), features JSONB | `Plan` | Seed 4 gói (99k/199k/299k/599k) |
| subscription_db | `subscription` | id, user_id, plan_id FK, start_at, end_at, status, payment_method, transaction_id | `Subscription` | FE type có `autoRenew` nhưng DB không có cột này (BE trả về hằng số) |
| subscription_db | `payment_transaction` | id, user_id, plan_id, amount, method, status, transaction_id UNIQUE, description | `PaymentTransaction` | |
| subscription_db | `activation_code` | id, code UNIQUE, plan_id FK, used_by_user_id, used_at, expires_at | `ActivationCode` | **Không có seed và không có API tạo mã** nên tính năng "Kích hoạt bằng mã" không thể dùng nếu không INSERT tay |

**Lệch entity ↔ schema:** không thấy lệch tên/kiểu cột gây lỗi `validate`. Lệch nhẹ: `Lesson.description` và `VocabDeck.description` không khai báo `columnDefinition="text"` (DB là TEXT; với PG driver vẫn qua validate). `files/init-db.sql` và `files/docker-compose.yml` (bản kế hoạch) **khác** bản ở thư mục gốc; bản ở gốc mới là bản dùng thật.

---

## 5. Component dùng lại & quy ước code

| Nhóm | File | Mô tả |
|---|---|---|
| UI | `components/ui/Button.tsx` | `variant` primary/secondary/danger/ghost/pro/warning/success, `size` sm/md/lg, `loading` |
| UI | `ui/Input.tsx` | `Input`, `Textarea`, `Select`, `FieldWrapper` (label/hint/error) |
| UI | `ui/Card.tsx`, `ui/Badge.tsx` (tone brand/green/amber/red/slate), `ui/Modal.tsx`, `ui/Spinner.tsx` (`Spinner`, `FullPageSpinner`) | |
| Layout | `layout/AppShell.tsx` (mới), `AuthGuard.tsx`, `Sidebar.tsx` (nav + mục admin theo `profile.isAdmin` + badge số từ cần ôn), `Topbar.tsx` (từ cần ôn, streak, PRO, logout), `PublicHeader.tsx` | |
| Audio | `audio/AudioPlayer.tsx` (fallback "Audio chưa khả dụng"), `audio/Recorder.tsx` (MediaRecorder `audio/webm`) | |
| Practice | `practice/WordDiffView.tsx`, `PhonemeScoreView.tsx`, `Flashcard.tsx` (lật 3D, WIP) | |
| Auth | `auth/GoogleSignInButton.tsx` (tự ẩn khi client id rỗng hoặc bắt đầu bằng `your_`) | |
| lib | `lib/api.ts` (axios instance, gắn Bearer, 401 → clearAuth + về `/login`, `apiErrorMessage`, `uploadToPresignedUrl`), `lib/endpoints/{auth,content,practice,vocabulary,subscription}.ts` (object `xxxApi` trả `r.data`), `lib/types.ts` | |
| store | `store/auth-store.ts` (zustand persist key `parroto-auth`: user, token, refreshToken, hasHydrated) | |

**Quy ước:**
- Đặt tên: component PascalCase, một component chính mỗi file, named export (riêng page dùng default export). Endpoint gom thành object `authApi/contentApi/...`. Query key là chuỗi kebab-case (`'vocab-stats'`, `'user-subscription'`), dùng chung giữa Sidebar/Topbar/Dashboard để chia sẻ cache.
- Styling: chỉ dùng Tailwind utility, palette tuỳ biến `brand` và `ink`, gradient `bg-pro-gradient`, `clsx` cho class điều kiện. Không có CSS module. Emoji làm icon.
- UI text bằng tiếng Việt; code/comment bằng tiếng Anh.
- Xử lý lỗi: `try/catch` hoặc `onError` của mutation → `toast.error(apiErrorMessage(err, '<fallback VN>'))`. Body lỗi chuẩn `{error, status, timestamp, requestId}` thống nhất giữa gateway và mọi `GlobalExceptionHandler` (Java). Java dùng `ApiException.notFound/forbidden/conflict/badRequest`.
- DTO: `types.ts` ghi "Mirrors the Java DTO records … exactly"; Java dùng `record` + Lombok `@Builder` + factory `from(entity)`; phân trang `PageResponse{data, pagination{page,limit,total,totalPages}}`, page bắt đầu từ 1. Chỗ lệch: `AttemptSummary` FE thiếu `createdAt`; FE `CardInput` thiếu `partOfSpeech`; response của `addFromLesson`, `addCards`, `addSentences` không được định kiểu (`any`).
- Mỗi service Java lặp lại cùng bộ khung: `config/{SecurityConfig, RequestIdFilter, RestAuthEntryPoint, RedisConfig}`, `security/{JwtAuthFilter, JwtValidator, AuthenticatedUser, AdminGuard}`, `service/CacheService`. Không có module dùng chung (code bị nhân bản 5 lần).

---

## 6. Test / build hiện có

| Hạng mục | Kết quả |
|---|---|
| Unit/integration test Java | **Không có**. `spring-boot-starter-test` có trong cả 5 pom nhưng không có thư mục `src/test` |
| Test frontend / gateway | **Không có** (không jest/vitest/playwright, không file `*.test.*`/`*.spec.*`) |
| Lint | Có script `next lint` nhưng **không có `.eslintrc`**; `next.config.js` đặt `eslint.ignoreDuringBuilds: true` |
| Type-check `npx tsc --noEmit` | **Không chạy**: `frontend/node_modules` không tồn tại (theo yêu cầu không `npm install`). Có file `frontend/tsconfig.tsbuildinfo` được commit (artifact build, nên cho vào `.gitignore`) |
| CI | Không có (`.github/` không tồn tại) |
| Docker/Maven build | Không chạy (theo yêu cầu) |

---

## 7. Vấn đề / nợ kỹ thuật

### Bug (chức năng hỏng)

| # | Mức | Vị trí | Mô tả |
|---|---|---|---|
| B1 | **Cao** | `frontend/src/components/audio/Recorder.tsx:11` vs `services/practice-service/.../ShadowingStorageService.java:52` | URL presigned được ký với `contentType("audio/wav")` nhưng FE PUT với `Content-Type: audio/webm`, nên S3 trả **403 SignatureDoesNotMatch**. Shadowing không bao giờ upload được |
| B2 | **Cao** | `AzureSpeechClient.java:62` | Gửi bytes WebM/Opus tới Azure với header `audio/wav; codecs=audio/pcm; samplerate=16000`, nên Azure không nhận dạng được. Cần chuyển sang WAV PCM 16kHz ở client hoặc transcode ở server (hoặc dùng `audio/ogg; codecs=opus`). Thêm nữa, Safari không hỗ trợ `mimeType: 'audio/webm'`, nên constructor ném lỗi |
| B3 | Trung bình | `frontend/src/app/(app)/practice/history/page.tsx:79` | `AudioPlayer src={detail.audioRecordingUrl}` nhưng giá trị là S3 key (`shadowing/<uid>/<id>.wav`), không phát được. Endpoint stream `/practice/shadowing/{id}/recording` lại cần Bearer, mà `<audio>` không gửi được |
| B4 | Trung bình | `app/lessons/[id]/page.tsx:20`, `(app)/lessons/[id]/dictation/[sentenceId]/page.tsx:32`, `.../shadowing/[sentenceId]/page.tsx:28`, `(app)/admin/lessons/[id]/page.tsx:65`, `(app)/profile/page.tsx:74` | Mẫu `if (isLoading \|\| !data) return <FullPageSpinner/>` khiến lỗi (404/502) thành **spinner vô hạn**, không có thông báo lỗi |
| B5 | Trung bình | `frontend/src/lib/api.ts:17-27` | Không có refresh token: access token hết hạn sau 1h thì bị logout cưỡng bức, dù đã lưu `refreshToken` và có `authApi.refreshToken` |
| B6 | Thấp | `frontend/src/components/audio/AudioPlayer.tsx:17-18` | `onError` gắn trên `<audio>` nhưng lỗi tải nằm ở `<source>` con (không bubble), nên fallback "Audio chưa khả dụng" có thể không bao giờ hiện |
| B7 | Thấp | `services/content-service/.../LessonService.java:123` | `addSentences` không xoá cache `lessons:list:*` (TTL 24h, dòng 24), nên `sentenceCount` ở danh sách bị cũ tới 24h |
| B8 | Thấp | `services/vocabulary-service/.../PremiumClient.java:44` | Cache premium 5 phút, không bị invalidate khi thanh toán, nên vừa mua xong vẫn bị 403 deck Pro tối đa 5 phút |
| B9 | Thấp | `frontend/src/components/layout/Sidebar.tsx:36` | Sidebar `hidden md:flex` và không có menu mobile, nên trên điện thoại không điều hướng được trong app |

### Bảo mật

| # | Vị trí | Mô tả |
|---|---|---|
| S1 | `api-gateway/middleware/auth.js:15`, `services/*/security/JwtValidator.java`, `auth-service/.../JwtAuthFilter.java:36` | Không kiểm tra claim `type`, nên **refresh token (7 ngày) dùng được như access token** ở mọi nơi. Chỉ `AuthService.refresh` (dòng 110) có kiểm tra |
| S2 | `api-gateway/middleware/auth.js`, các `JwtValidator` ngoài auth | Logout chỉ đưa jti vào blacklist mà auth-service kiểm tra; gateway và 4 service còn lại vẫn chấp nhận token đã logout tới khi hết hạn |
| S3 | `practice-service/.../ShadowingController.java:41-43`, `ShadowingService.java:82` | `GET /practice/shadowing/{attemptId}/recording` không kiểm tra `userId` (IDOR): mọi user đăng nhập đều nghe được bản ghi của người khác nếu biết attemptId |
| S4 | `auth-service/.../GoogleTokenVerifier.java:43`, `AppleTokenVerifier.java:52` | Nếu `GOOGLE_CLIENT_ID`/`APPLE_CLIENT_ID` rỗng thì **bỏ qua kiểm tra `aud`**, nên chấp nhận ID token cấp cho app bất kỳ. Thêm nữa, `APPLE_CLIENT_ID` không được truyền vào auth-service trong `docker-compose.yml` |
| S5 | `docker-compose.yml:100,151,201,246,304,40` + `SecurityConfig` (auth:45, subscription:39) | Các cổng 8001–8005 và Redis 6379 publish ra host; `/internal/**` là `permitAll`, nên bỏ qua được gateway (đọc email user, trạng thái premium) |
| S6 | `init-db.sql:8-12` | Mật khẩu role DB hard-code (`auth_password`…) và không đọc từ `.env`. Hiện `.env` trùng giá trị nên chạy được, nhưng đổi `*_DB_PASSWORD` trong `.env` sẽ làm service không kết nối được |
| S7 | `docker-compose.yml` (JWT_SECRET default), `api-gateway/middleware/auth.js:4` | Fallback secret `dev_super_secret_jwt_key_change_in_production` nằm trong code |
| S8 | `SubscriptionService.handlePaymentCallback` | Không đối chiếu `vnp_Amount` với `transaction.amount`; chỉ có return URL (không có IPN server-to-server), nên người dùng đóng trình duyệt sau khi trả tiền thì giao dịch vẫn `pending` |
| S9 | `vocabulary-service/.../ReviewService.java:99` | `add-from-lesson` không kiểm tra deck Pro, nên user free vẫn thêm được thẻ Pro nếu biết `cardId` |
| S10 | Frontend `(app)/admin/*` | Không có guard `isAdmin` phía client (backend vẫn chặn bằng 403, nên chỉ ảnh hưởng UX) |

### Nợ kỹ thuật / giá trị hard-code

| Vị trí | Mô tả |
|---|---|
| `docker-compose.yml:81-82` | `JWT_EXPIRY_SECONDS: 3600`, `JWT_REFRESH_EXPIRY_SECONDS: 604800` hard-code, bỏ qua giá trị trong `.env` |
| `services/*/Dockerfile` | Build bằng JDK/JRE **25** trong khi `java.version` = 21 |
| `api-gateway/package.json` | Dải `^`, không lockfile, `npm install` trong Dockerfile nên build không tái lập được |
| `content-service application.yml:25` | `max-file-size: 5MB`, quá nhỏ cho audio bài học |
| `frontend/.../vocabulary/decks/[id]/page.tsx:43,48` | `limit: 100` thẻ, `limit: 500` từ đã học (cắt cứng); `notebook/page.tsx:20` `limit: 100` |
| `vocabulary/page.tsx:10`, `decks/[id]/page.tsx:18` | Mảng `TOPIC_ICONS` bị nhân bản; icon gán theo index thay vì dữ liệu |
| `CEFR_LEVELS` | Khai báo lặp ở 5 file (`register`, `lessons`, `profile`, `admin/lessons`, `admin/vocabulary`) |
| Phân trang | Đoạn JSX phân trang copy ở `lessons/page.tsx`, `practice/history`, `admin/lessons` |
| `init-db.sql` seed | `audio_url` trỏ `https://parroto-dev-bucket.s3.amazonaws.com/...` không tồn tại; không seed `activation_code`; không seed user admin |
| `CacheService.invalidateByPattern` (cả 4 service) | Dùng lệnh Redis `KEYS` (chặn Redis khi dữ liệu lớn) |
| Mã Java | `SecurityConfig/JwtValidator/RequestIdFilter/CacheService/GlobalExceptionHandler` nhân bản ở 5 service |
| `"user".total_hours_learned/total_words_learned` | Không được đồng bộ từ practice/vocabulary, nên Profile hiển thị sai |
| Thiếu `not-found.tsx`/`error.tsx`, thiếu test, thiếu CI, thiếu `.eslintrc`; `frontend/tsconfig.tsbuildinfo` bị commit | |
| Không có TODO/FIXME nào trong mã nguồn | |
