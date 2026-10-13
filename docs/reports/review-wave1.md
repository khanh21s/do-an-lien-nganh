# Review Wave 1 (T01–T06) — log summary

- Date: 2026-10-13 · Branch `dev/multi-agent` · HEAD at start `03ef1ae` · reviewer fix `3350240`
- Result: **104 checks — 100 PASS, 2 FAIL (1 Medium, 1 Low), 1 N/A, 1 blocker FIXED**
- Task status: T01 DONE · T02 DONE-WITH-ISSUES · T03 DONE · T04 DONE-WITH-ISSUES · T05 DONE-WITH-ISSUES · T06 DONE-WITH-ISSUES (details in `docs/tasks.md` → each task's Review notes and "## Review Wave 1")

## 1. Environment
- Old stack stopped and volumes wiped (`docker-compose down -v`; backup already in `../parroto-backup/`).
- `.env` compared with `.env.example` by key name only. Missing keys were appended (existing values kept): `INTERNAL_TOKEN` (random 48 hex chars), `REDIS_URL=redis://redis:6379`, `REDIS_TIMEOUT=2s`, `STORAGE_PROVIDER=auto`, `SPEECH_PROVIDER=auto`, `MEDIA_ROOT=/data/media`. DB role passwords were already present. `JWT_SECRET` is ≥ 32 chars (68) so it was kept.
- AWS/Azure keys in `.env` are `your_…` placeholders → practice/content run in local storage + mock scorer mode (as intended for the demo). VNPay keys are placeholders too, which is enough for the local IPN script (same secret on both sides).
- `docker-compose up -d --build`: all 8 back-end containers healthy, frontend up (no healthcheck), in ~4 min. Only host ports 3000/8000 published.
- The first `up -d --build` exited 1 with no captured output (likely a transient build failure while other agents were building in parallel); an immediate re-run succeeded with no changes.
- Note: the snap `docker-compose` binary writes nothing when its stdout is redirected to a file under `/tmp` (snap private /tmp); pipe through `cat` instead.

## 2. E2E run 1 → blocker
Every authenticated call through the gateway returned `401 Invalid or expired token`. Cause: auth-service `JwtService` signed with `.signWith(key)`; jjwt picks the algorithm from the key length, so a 68-byte `JWT_SECRET` produced **HS512** tokens, while the gateway (T02) verifies with `algorithms: ['HS256']` (architecture §5.2 says HS256). Unit tests used a 47-byte secret, so they never hit it.
Fix `3350240`: `.signWith(key, Jwts.SIG.HS256)` + unit test `alwaysSignsWithHs256EvenForLongSecrets` (`scripts/verify-java.sh auth-service` green); auth-service rebuilt.

## 3. E2E run 2 (after fix)
Python smoke script (stdlib only) against `http://localhost:8000` and `http://localhost:3000`; internal-network checks run with `docker exec parroto-api-gateway node -e fetch(...)`; VNPay via `scripts/vnpay-ipn-test.sh`. Admin tests used `admin@parroto.com` on a throw-away lesson/deck (deleted afterwards); seed lessons/decks were not modified. Demo codes `PROMO-DEMO1-MONTH`, `PROMO-DEMO3-MONTH`, `PROMO-DEMOY-EARLY` are now **used** by test accounts (re-seed with `down -v` if needed for a demo).

Extra manual checks: premium cache eviction (free user → Pro card 403 → `premium:{id}` present → activate code → key gone → Pro card 201), review-queue item fields, gateway exit on empty/short `JWT_SECRET`, `grep .keys(` clean, `scripts/verify-fe.sh` (tsc + lint 0 warnings + vitest 25/25), service log scan (no ERROR lines; only 500s were the malformed-UUID probes below).

### Failed checks
| ID | Severity | Summary | Repro | Location |
|---|---|---|---|---|
| AUTH-03-REFRESH | Medium | Refresh token still valid after logout (access token is revoked correctly) | login → `POST /auth/logout` (Bearer) → `POST /auth/refresh-token {"refreshToken":"<old>"}` → 200 | `services/auth-service/src/main/java/com/parroto/auth/service/AuthService.java:123-131` |
| ERR-UUID | Low | Malformed UUID / unmapped route → 500 | `curl :8000/content/lessons/abc`, `curl :8000/vocabs/decks/xyz/cards`, `curl :8000/vocabs/decks/<id>` → 500 | content `exception/GlobalExceptionHandler.java:55`, vocabulary `exception/GlobalExceptionHandler.java:38` |

N/A: AUTH-04 (blank `GOOGLE_CLIENT_ID` → 503) cannot be exercised with the user's `.env` (placeholder `your_…` → 401 "Invalid Google token"); code path covered by unit test. Recorded as Low under T02.

## 4. Static review (`git diff 1e2bac5..HEAD -- services api-gateway frontend/src/lib`)
Focus on correctness/security vs spec. Findings beyond the ones above:
- Gateway auth (`middleware/auth.js`, `redis.js`): HS256 pin, `type=access`, `jti` required, blacklist with bounded 500 ms wait + fail-open — OK (the HS256 pin is what exposed the auth-service bug).
- Java `JwtAuthFilter`/`JwtValidator` (4 services) and `InternalTokenFilter` (constant-time SHA-256 compare, decoded servlet path) — OK. `/error` permitted everywhere (integration commit c22bcd4).
- practice: local upload capped at 2 MB with stream read, WAV validated before scoring, path-traversal guard, owner check `findByIdAndUserId` — OK. Known/accepted: any UUID can be PUT into the caller's own folder; files never cleaned up (T03 report).
- content: `local:` routing, traversal guard, Range 206/416, seed files never deleted — OK.
- subscription: `processVnpayResult` with row lock, 97/01/02/04/00, amount check ×100, constant-time signature compare, eviction + email after commit — OK. Return URL runs the same idempotent processing — OK.
- vocabulary: SM-2 per §4.6, Pro gating, `userVocabId` enrichment bypasses shared cache for authenticated requests — OK.
- FE `src/lib`: single-flight refresh, retry re-reads the current token, `safeNextPath` rejects `//` and `/\` — OK.
- Not a bug, FYI: compose sets `LOGGING_LEVEL_COM_PARROTO: DEBUG` for every Java service, so logs are very verbose in the demo stack.

## 5. Raw output of E2E run 2
```
PASS AUTH-REG register user A | 201
PASS AUTH-REG-B register user B | 201
PASS AUTH-ADMIN register/login admin@parroto.com | 200
PASS AUTH-LOGIN login user A | 200
PASS AUTH-PROFILE GET /auth/profile | 200
PASS AUTH-02-GW refresh token as Bearer on /practice/stats (gateway) -> 401 | 401
PASS AUTH-02-SVC refresh token as Bearer direct to practice-service -> 401 | 401
PASS AUTH-NOTOKEN no token /practice/stats -> 401 | 401
PASS AUTH-04 login-google with placeholder GOOGLE_CLIENT_ID -> 503 (or 401 if treated as configured) | 401 b'{"error":"Invalid Google token","status":401,"timestamp":"2026-10-13T03:29:33.773001684Z","requestId":"6bc2d6d7-4128-4679-8ab8-0c2354529bcf"}'
PASS X-02-AUTH-NOTOKEN auth /internal/users/<id> without token -> 401 | 401
PASS X-02-AUTH-TOKEN auth /internal/users/<id> with token -> 200 | 200
PASS X-02-SUB-NOTOKEN subscription /internal/... without token -> 401 | 401
PASS X-02-SUB-WRONG subscription /internal/... wrong token -> 401 | 401
PASS X-02-GW gateway does not expose /internal (404) | 404
PASS SUB-PLANS plans = 79000/169000/499000, no lifetime | 200 [79000, 169000, 499000]
PASS LES-LIST GET /content/lessons | 200 n=5
PASS LES-DETAIL GET /content/lessons/{id} with sentences | 200 n=5
PASS LES-08-AUDIO seed lesson audio 200 audio/mpeg | 200 audio/mpeg 81547
PASS LES-RANGE audio with Range -> 206 + Content-Range | 206 bytes 0-99/81547 100
PASS LES-SENT-AUDIO seed sentence audio 200 | 200 audio/mpeg
PASS DIC-07-Easy dictation perfect Easy -> 60 | 201 score=60.0
PASS DIC-07-Normal dictation perfect Normal -> 80 | 201 score=80.0
PASS DIC-07-Hard dictation perfect Hard -> 100 | 201 score=100.0
PASS SHA-URL request-upload-url storage=local uploadRequiresAuth=true | 200 {'uploadUrl': '/practice/shadowing/recordings/c63f4534-398c-4b30-b581-d4ce07b3fcdf', 'recordingId': 'c63f4534-398c-4b30-b581-d4ce07b3fcdf', 'expiresIn': 3600, 'uploadMethod': 'PUT', 'uploadRequiresAuth': True, 'contentType': 'audio/wav', 'storage': 'local'}
PASS SHA-PUT-WEBM PUT non-WAV -> 400 | 400 b'{"error":"\xc4\x90\xe1\xbb\x8bnh d\xe1\xba\xa1ng ghi \xc3\xa2m kh\xc3\xb4ng h\xe1\xbb\xa3p l\xe1\xbb\x87 (c\xe1\xba\xa7n WAV PCM 16 kHz mono)","status":400,"timestamp":"2026-10-13T03:'
PASS SHA-PUT-48K PUT 48 kHz WAV -> 400 | 400 b'{"error":"\xc4\x90\xe1\xbb\x8bnh d\xe1\xba\xa1ng ghi \xc3\xa2m kh\xc3\xb4ng h\xe1\xbb\xa3p l\xe1\xbb\x87 (c\xe1\xba\xa7n WAV PCM 16 kHz mono)","status":400,"timestamp":"2026-10-13T03:'
PASS SHA-PUT-NOAUTH PUT without Bearer -> 401 | 401
PASS SHA-PUT PUT 16k mono WAV with Bearer -> 204 | 204 b''
PASS SHA-SUBMIT submit shadowing -> scorer=mock with word scores | 201 scorer=mock score=98.29
PASS SHA-OWNER owner streams recording -> 200 audio/wav | 200 audio/wav
PASS SHA-04 other user's recording -> 404 | 404
PASS PRA-03-90 activity?days=90 -> 90 entries | 200 n=90
PASS PRA-03-TODAY today's activity counts the attempts | {'date': '2026-10-13', 'attempts': 4, 'minutes': 0}
PASS PRA-03-400 activity?days=400 -> 400 | 400
PASS PRA-05 GET /practice/attempts (no lessonId) 200 + difficulty | 200 [{'attemptId': '8e04ab34-49ca-4457-9048-dc85aa948fd6', 'lessonId': '11111111-1111-1111-1111-111111111101', 'sentenceId': '83742dfe-ad54-4fa7-9ed0-9907fce3d0fa', 'mode': 'shadowing', 'score': 98.29, 'accuracy': 98.29, 'difficulty': None, 'createdAt': '2026-10-13T03:29:35.23608Z'}]
PASS PRA-STATS GET /practice/stats 200 | 200
PASS PRA-07 GET /practice/streak 200, current>=1 | 200 b'{"currentStreak":1,"lastPracticeDate":"2026-10-13T03:29:35.23608Z"}'
PASS VOC-DECKS GET /vocabs/decks | 200 n=3
PASS VOC-CARDS GET deck cards | 200 n=8
PASS VOC-PRO-LIST Pro deck cards listable (preview) or 403 | 403
PASS VOC-ADD add 4 cards to notebook | {0: 'ca910950-35d9-435b-b630-46ce4260233e', 1: '15a63e39-0d85-46c3-b86f-90ceabe82d19', 2: 'ec554875-58dc-4e81-9ed3-40386680f2c6', 3: '559ce6e7-8019-4680-b203-1b54f01aed71'}
PASS VOC-04 deck cards with Bearer expose userVocabId for learned cards | n=4
PASS VOC-01-RATINGS review ratings 1/3/4/5 all 200 | {1: 200, 3: 200, 4: 200, 5: 200}
PASS VOC-01-N1 second Good review -> interval 6 | {'userVocabId': 'ec554875-58dc-4e81-9ed3-40386680f2c6', 'nextReviewAt': '2026-10-19T03:29:35.86333523Z', 'easeFactor': 2.5, 'interval': 6, 'repetitions': 2, 'message': 'Tốt lắm! Ôn lại trong 6 ngày.'}
PASS VOC-01-AGAIN Again resets -> interval 1, repetitions 0 | {'userVocabId': 'ec554875-58dc-4e81-9ed3-40386680f2c6', 'nextReviewAt': '2026-10-14T03:29:35.877000887Z', 'easeFactor': 1.96, 'interval': 1, 'repetitions': 0, 'message': 'Đừng lo! Thẻ sẽ được ôn lại sau 1 ngày.'}
PASS VOC-07 review-queue 200 with new fields | {'queue': [], 'totalDueCards': 0, 'totalCards': 4}
PASS VOC-STATS vocabs/user/stats 200 | 200
PASS SUB-05-ACT activate demo code -> premium | 200 b'{"subscription":{"id":"3022545f-51e6-4a88-871b-0fac41730be6","planId":"plan-1month","planName":"1 th\xc3\xa1ng Premium","startAt":"2026-10-13T03:29:36.186010623Z","endAt":"2026-11-12T03:29:36.186010623Z","s'
PASS SUB-USER subscriptions/user isPremium | 200
PASS SUB-05-REUSE reuse code -> 400 'Mã đã được sử dụng' | 400 b'{"error":"M\xc3\xa3 \xc4\x91\xc3\xa3 \xc4\x91\xc6\xb0\xe1\xbb\xa3c s\xe1\xbb\xad d\xe1\xbb\xa5ng","status":400,"timestamp":"2026-10-13T03:29:36.268103024Z","requestId":"80fb6805-220b-4f31-be6c-3b660f590458"}'
PASS SUB-05-UNKNOWN unknown code -> 404 | 404
PASS SUB-05-ACCUM second code accumulates (daysRemaining ~ 30+90) | 200 119
PASS ADM-06-403 normal user create activation codes -> 403 | 403
PASS ADM-06-403-LIST normal user list activation codes -> 403 | 403
PASS ADM-03-403-PUT normal user PUT sentence -> 403 | 403
PASS ADM-03-403-DEL normal user DELETE sentence -> 403 | 403
PASS ADM-05-403 normal user DELETE card -> 403 | 403
PASS ADM-07-403 normal user POST deck -> 403 | 403
PASS ADM-LES-403 normal user POST lesson -> 403 | 403
PASS ADM-06-CREATE admin creates 10 unique codes | 201 {'codes': [{'id': '4c55b213-5277-43c3-866a-948ed4a8704a', 'code': 'PROMO-ECA9W-MDKNW', 'planId': 'plan-1month', 'planName': '1 tháng Premium', 'usedByUserId': None, 'usedByEmail': None, 'usedAt': None
PASS ADM-06-LIST list used=true shows usedByEmail | 200 [{'id': 'd1a6b833-d09a-491e-bd38-deec99b906f4', 'code': 'PROMO-DEMO1-MONTH', 'planId': 'plan-1month', 'planName': '1 tháng Premium', 'usedByUserId': '9be3d85c-d044-4d81-a889-ece4da5285a3', 'usedByEmai
PASS ADM-LES-CREATE admin creates lesson | 201 b'{"id":"c5b314cd-9acb-4e3f-8c09-d56f299391cc","title":"RW1 test 1791862171","sourceUrl":null,"cefrLevel":"A1","topicTag":"greetings","description":"rev'
PASS ADM-SENT-ADD admin adds 5 sentences | 201 b'[{"id":"41bf2d1c-490f-4da6-ac75-38a45e369534","sentenceOrder":1,"text":"Sentence 0","ipa":null,"translationVi":null,"startMs":0,"endMs":900,"audioUrl"'
PASS LES-07-COUNT list sentenceCount=5 right after add (cache evicted) | [5]
PASS ADM-03-PUT admin PUT sentence 200 | 200 b'{"id":"41bf2d1c-490f-4da6-ac75-38a45e369534","sentenceOrder":1,"text":"Sentence 0","ipa":null,"translationVi":null,"startMs":100,"endMs":777,"audioUrl'
PASS LES-07-PUT detail shows new endMs immediately | [{'id': '41bf2d1c-490f-4da6-ac75-38a45e369534', 'sentenceOrder': 1, 'text': 'Sentence 0', 'ipa': None, 'translationVi': None, 'startMs': 100, 'endMs': 777, 'audioUrl': None, 'imageUrl': None}]
PASS ADM-03-VALID startMs>=endMs -> 400 | 400
PASS ADM-03-DEL delete sentence 2/5 -> orders 1..4 | 200 [1, 2, 3, 4]
PASS LES-07-COUNT-DEL list sentenceCount=4 after delete | [4]
PASS ADM-04-20MB admin upload 20MB mp3 -> 200 | 200 b'{"audioUrl":"local:lessons/c5b314cd-9acb-4e3f-8c09-d56f299391cc/970801be-8f92-43e5-b05a-209fc8f4b18c.mp3","duration":14}'
PASS ADM-04-PLAY uploaded audio streams with Range 206 | 206 bytes 0-1023/20971520
PASS ADM-04-EXE upload .exe -> 400 | 400 b'{"error":"File ph\xe1\xba\xa3i l\xc3\xa0 audio (mp3, m4a, wav, ogg, webm)","status":400,"timestamp":"2026-10-13T03:29:37.922692011Z","requestId":"b47bb13f-c16a-47ce-'
PASS ADM-LES-DEL admin deletes test lesson | 200
PASS ADM-07 admin creates deck | 201 b'{"id":"a58a430b-9270-4273-83d1-093e31132831","name":"RW1 deck 1791862171","description":null,"level":"A1","isProOnly":false,"cardCount":0,"createdAt":'
FAIL ADM-05 delete card -> card_count 3->2 | 200 None
PASS SUB-PAY free user create-payment | 200 {'transactionId': '140c46c3-1fcc-40fc-9840-f84c8abd95bf', 'paymentUrl': 'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=7900000&vnp_Com
PASS SUB-02-97 IPN bad signature -> 97 | tatus=00&vnp_TxnRef=140c46c3-1fcc-40fc-9840-f84c8abd95bf&vnp_SecureHash=0dc42f1ade1ae967475b1bf6fc2cb617eb03e729342cc3824594189fce533b25ce05b1e123b757d216ff55a40bdd6c335eb11c2e055830e3c6cec79eb0f5085c
PASS SUB-02-04 IPN wrong amount -> 04 | tatus=00&vnp_TxnRef=140c46c3-1fcc-40fc-9840-f84c8abd95bf&vnp_SecureHash=d595581db559d8e27832cb631ff04f4a731d1a5b53bd63bc0e4f942c9cd86b0eaff71dad3d031b613e249658850fb27632095995575a595cf4fbc044e67afcb9
PASS SUB-02-04-NOSUB after wrong amount B still not premium | b'{"subscription":null,"isPremium":false}'
PASS SUB-02-00 IPN valid -> 00 | tatus=00&vnp_TxnRef=2210de1c-cb6f-441c-a16d-51622a9b5b6e&vnp_SecureHash=d108dd23b70cfd75410fab9ab806c0881ed5c7c63add66779dd054fac155d00a0ae762691739f75e1ab64756db3cf80afcd1cbbeb67f3759b4a983ec0e1b498d
PASS SUB-02-02 IPN replay -> 02 | tatus=00&vnp_TxnRef=2210de1c-cb6f-441c-a16d-51622a9b5b6e&vnp_SecureHash=25b0e9b82b62c20f8a5c172d47f7c6da749b4c3b51091d4b4ed74abaeb2cb54ab5f37566c879c90f734a9de485f5c2e9b42a8ab58687c81a9994a1f4b46e3569
PASS SUB-02-PREM B premium after IPN | b'{"subscription":{"id":"d6541ab8-bc42-4984-ad4d-0adbbcb06cc2","planId":"plan-1month","planName":"1 th\xc3\xa1ng Premium","startAt":"2026-10-13T03:29:38.28047'
PASS SUB-02-01 IPN unknown txn -> 01 | tatus=00&vnp_TxnRef=445e8593-75cf-4045-b37f-7c8cf5050c61&vnp_SecureHash=d966258dd796742d9a85e4a55cc0500cbdf1192bdfb0fd75be7281c9a483e686350e1a22d2e0d1bac50426709a1bf906627d8a148677591369141ee06ff8888a
PASS AUTH-03-LOGOUT logout 200 | 200 b'{"message":"Logged out successfully"}'
PASS AUTH-03-practice-stats old token after logout /practice/stats -> 401 | 401
PASS AUTH-03-vocabs-user-stats old token after logout /vocabs/user/stats -> 401 | 401
PASS AUTH-03-subscriptions-user old token after logout /subscriptions/user -> 401 | 401
PASS AUTH-03-auth-profile old token after logout /auth/profile -> 401 | 401
PASS AUTH-03-SVC old token direct to vocabulary-service -> 401 | 401
FAIL AUTH-03-REFRESH refresh token after logout rejected | 200
PASS FE- frontend / -> 200 | 200
PASS FE-login frontend /login -> 200 | 200
PASS FE-lessons frontend /lessons -> 200 | 200
PASS FE-404 frontend unknown route -> 404 page | 404

TOTAL 94 PASS 92 FAIL 2
```

(Run 2 FAIL `ADM-05` was a script error: it read a non-existent `GET /vocabs/decks/{id}`; re-checked manually via `GET /vocabs/decks` → `cardCount` 3→2 after delete = PASS.)
