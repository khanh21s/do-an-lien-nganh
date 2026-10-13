# Review Final (Phase 1, T01–T12 + FIX-R1) — log summary

- Date: 2026-10-13 · Branch `dev/multi-agent` · HEAD at start `d838bb5` · reviewer fixes `208b400`, `b701cfc`
- Result: **API 46/46 PASS + VNPay IPN 4/4 PASS · browser E2E 10/10 PASS** (after fixes) · 1 High + 1 Low found and **fixed** · 2 Low open
- Task status: T01 DONE · T02 DONE · T03 DONE · T04 DONE-WITH-ISSUES · T05 DONE · T06 DONE-WITH-ISSUES · T07–T12 DONE (details in `docs/tasks.md` → Review notes and "## Review Final")

## 1. Environment
- `docker-compose down -v && docker-compose up -d --build` → all 8 back-end containers healthy, frontend up; demo codes re-seeded. Local storage + mock scorer (AWS/Azure keys are placeholders).
- The user's `.env` has `LOG_LEVEL=DEBUG` — left as is (Java services now default to INFO via `APP_LOG_LEVEL`, `bb9bbd8`).
- Service logs after all runs: 0 `ERROR` lines in auth/content/practice/vocabulary/subscription.
- Seeded demo codes `PROMO-DEMO1-MONTH`, `PROMO-DEMO3-MONTH`, `PROMO-DEMOY-EARLY` are now **used** by E2E users (re-seed with `down -v` before a demo). Test users `final_a_*`, `vnp_*`, `e2e_*@test.local`; admin `admin@parroto.com` registered by the tests.

## 2. API regression — `scripts/api-smoke.py` (stdlib only, does not consume demo codes)
Wave 1 smoke subset + FIX-R1 items. Output of the final run (after the gateway fix):
```
PASS AUTH-REG register user A | 201
PASS AUTH-ADMIN register/login admin | 200
PASS AUTH-PROFILE profile | 200
PASS AUTH-PROFILE-ADMIN admin profile isAdmin | b'{"userId":"b12f2103-7178-43cb-b5a4-dcc080674fdc","email":"admin@parroto.com","cefrLevel":"B1","authP
PASS AUTH-02-GW refresh token as Bearer -> 401 | 401
PASS AUTH-NOTOKEN no token -> 401 | 401
PASS AUTH-04 login-google with placeholder client id -> 503 | 503
PASS SUB-PLANS plans 79000/169000/499000 | [79000, 169000, 499000]
PASS LES-LIST lessons list | 200 n=5
PASS LES-DETAIL lesson detail sentences | 5
PASS DIC-07-Easy perfect Easy -> 60 | 60.0
PASS DIC-07-Normal perfect Normal -> 80 | 80.0
PASS DIC-07-Hard perfect Hard -> 100 | 100.0
PASS SHA-URL request upload url | local
PASS SHA-PUT PUT wav -> 204 | 204
PASS SHA-SUBMIT submit -> mock with wordScores | 201 mock
PASS PRA-03 activity 90 | 200
PASS PRA-07 streak >= 1 | b'{"currentStreak":1,"lastPracticeDate":"2026-10-13T04:01:17.465652Z"}'
PASS PRA-05 attempts list | 200
PASS VOC-DECKS decks | 3
PASS VOC-CARDS deck cards | 8
PASS VOC-ADD add card | b'{"userVocabId":"5000f93b-b951-4137-857d-b15d789ef8a2","cardId":"83aefc68-8d95-44f4-ac3c-cf3fd3467f15'
PASS VOC-REVIEW review q4 | b'{"userVocabId":"5000f93b-b951-4137-857d-b15d789ef8a2","nextReviewAt":"2026-10-14T04:01:17.565182412Z'
PASS FIX-VOC-05 learned includes nextReviewAt/intervalDays/easeFactor | {'nextReviewAt': '2026-10-14T04:01:17.565182Z', 'intervalDays': 1, 'easeFactor
PASS VOC-PRO free user pro deck cards -> 403 | 403
PASS FIX-ERR-LES GET /content/lessons/abc -> 400 | 400 b'{"error":"Tham s\xe1\xbb\x91 kh\xc3\xb4ng h\xe1\xbb\xa3p l\xe1\xbb\x87","status":400,"timesta
PASS FIX-ERR-DECK GET /vocabs/decks/xyz/cards -> 400 | 400 b'{"error":"Tham s\xe1\xbb\x91 kh\xc3\xb4ng h\xe1\xbb\xa3p l\xe1\xbb\x87","status":400,"tim
PASS FIX-ERR-DECKGET GET /vocabs/decks/22222222-2222-2222-2222-222222222201 -> 404/405 | 405 b'{"error":"Ph\xc6\xb0\xc6\xa1ng th\xe1\xbb\xa9c kh\xc3\x
PASS FIX-ERR-404 GET /content/no-such-route -> 404 | 404 b'{"error":"Kh\xc3\xb4ng t\xc3\xacm th\xe1\xba\xa5y t\xc3\xa0i nguy\xc3\xaan","status":404,"t
PASS FIX-ERR-404V GET /vocabs/nope/nope -> 404 | 404 b'{"error":"Route not found","status":404,"timestamp":"2026-10-13T04:01:17.678Z","'
PASS FIX-ERR-GW404 GET /nonexistent-route -> 404 | 404 b'{"error":"Route not found","status":404,"timestamp":"2026-10-13T04:01:17.680Z","'
PASS ADM-06-CREATE admin creates codes | 201
PASS ADM-06-403 non-admin -> 403 | 403
PASS SUB-05-ACT activate admin code | 200
PASS SUB-05-REUSE reuse -> 400 | b'{"error":"M\xc3\xa3 \xc4\x91\xc3\xa3 \xc4\x91\xc6\xb0\xe1\xbb\xa3c s\xe1\xbb\xad d\xe1\xbb\xa5ng","status":400,"tim
PASS SUB-USER isPremium | b'{"subscription":{"id":"b717c396-8580-4f31-890c-be89de1d64bd"'
PASS AUTH-03-LOGOUT logout with refreshToken body | 200
PASS AUTH-03-ACCESS old access -> 401 | 401
PASS AUTH-03-REFRESH old refresh after logout -> 401 | 401
PASS AUTH-03-NOBODY logout without body -> 200 | 200
PASS AUTH-REFRESH-OK refresh token of another session still works when not sent (expected 200) | 200
PASS FE/ FE / | 200
PASS FE/login FE /login | 200
PASS FE/lessons FE /lessons | 200
PASS FE/pricing FE /pricing | 200
PASS FE/no-such-page FE /no-such-page | 404

TOTAL 46 PASS 46 FAIL 0
```
VNPay (`scripts/vnpay-ipn-test.sh`, new free user): bad signature → 97, wrong amount → 04, valid → 00 (premium), replay → 02.

## 3. Browser E2E — `e2e/` (Playwright 1.47.2 in `mcr.microsoft.com/playwright:v1.47.2-jammy`, Chromium `--use-fake-ui-for-media-stream --use-fake-device-for-media-stream`)
Run: `ADMIN_PASSWORD=… DEMO_CODE=PROMO-DEMO1-MONTH e2e/run.sh` (see `e2e/README.md`). The project lives in the repo because the snap Docker daemon cannot bind-mount `/tmp`; outputs are git-ignored. Between phase 1 (tests 01–05) and phase 2 (05b–09) `run.sh` sets the new user's `user_vocab.next_review_at` to the past so the review queue is not empty.

| # | Scenario | Result |
|---|---|---|
| 01 | register → `/dashboard`; weekly SVG chart; exactly 90 heatmap cells; Topbar streak chip | PASS |
| 02 | `/lessons` A1 chip → `?level=A1`; search "Greet" → `?q=Greet`; open lesson → "Luyện Dictation" | PASS |
| 03 | sentence audio loadable from FE origin; Normal blanks + Enter → "🎉 Chính xác!" 80/80; Hard (via ⚙) + Enter → 100/100; IPA not `//…//`; `Shift+→` → next sentence; wrong → "Chưa đúng, thử lại"; finish → `/result?mode=dictation` ("Đã làm x/5 câu") | PASS (after fixes) |
| 04 | shadowing: record 2.2 s with fake mic → per-word scores + "🧪 Chế độ demo – điểm mô phỏng"; history → detail modal `<audio src=blob:…>` plays | PASS |
| 05 | deck: Space flips, 1–4 rate (4 cards) | PASS |
| 05b | review: flip + 1–4 → summary (distribution, next review time); notebook shows "Ôn tiếp: …" + "Chu kỳ N ngày" | PASS |
| 06 | `/pricing` 79.000đ / 169.000đ / 499.000đ; activate demo code → Topbar PRO; reuse → "Mã đã được sử dụng" | PASS |
| 07 | admin: edit title of a throw-away lesson → persisted (then deleted); "Tạo mã" → new `PROMO-…` codes; non-admin `/admin/lessons` → `/dashboard` | PASS |
| 08 | 375×812: tab bar, "Thêm" drawer (dialog); tab bar hidden on dictation; 360px: `scrollWidth <= innerWidth` on 10 pages | PASS |
| 09 | invalid access token + valid refresh token → protected pages load, exactly 1 `/auth/refresh-token`, new token stored; UI logout → old refresh token 401 | PASS |
| — | `pageerror` count | 0 |
| — | console errors | only expected resource logs: 400 (reused code, test 06), 5× 401 before the single refresh (test 09) |

Screenshots (20): `/tmp/claude-1001/-home-KHANHDV3-English/a4c66d39-b997-41ec-8495-c5dc31e5faae/scratchpad/e2e/screenshots/` (also regenerated in `e2e/screenshots/`, git-ignored).

## 4. Bugs found
| ID | Severity | Summary | Status |
|---|---|---|---|
| RF-01 | High | Gateway `helmet()` sends `Cross-Origin-Resource-Policy: same-origin`; FE (`:3000`) loads lesson/sentence audio from the gateway (`:8000`) via `<audio>` → Chrome blocks it (`ERR_BLOCKED_BY_RESPONSE.NotSameOrigin`), so **no lesson audio played** (dictation/shadowing/transcript). Not caught by curl-based Wave 1. | FIXED `208b400` (`crossOriginResourcePolicy: cross-origin`; HEAD/OPTIONS allowed by `requireAuthForWrites`) |
| RF-02 | Low | Sentence IPA rendered `//həˈloʊ …//` (seed IPA already contains slashes) in dictation, shadowing, lesson transcript | FIXED `b701cfc` (verify-fe: tsc, lint, vitest 134/134) |
| RF-03 | Low | `HEAD /content/sentences/{id}/audio` → 401: content-service `SecurityConfig` permits only `HttpMethod.GET` | OPEN (no browser impact) |
| RF-04 | Low (a11y) | `FieldWrapper` in `frontend/src/components/ui/Input.tsx` renders `<label>` without `htmlFor`, so labels are not tied to inputs | OPEN |

## 5. Not covered in the browser
Sidebar collapse persistence, review popup once/day, guest `PublicHeader` on `/lessons` and `/pricing`, VNPay redirect pages, admin audio upload / sentence edit / deck cards UI (covered by API tests and unit tests).
