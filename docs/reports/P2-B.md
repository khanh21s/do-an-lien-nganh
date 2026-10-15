# P2-B: app-wide gamification (XP, level, diamonds, check-in, daily missions)

Branch `feat/phase2-b-gamification`. This builds spec §5 group 1 (core) inside **vocabulary-service**, as the user decided. The service already had the "Dịch nhanh" XP/level.

- XP is shared by all activities.
- The virtual currency is "kim cương" (diamonds). You earn it from the daily check-in and from daily missions.
- There is no shop yet. Diamonds only accumulate. The ledger already allows negative rows, so a future shop can spend them.

## 1. Design

```
practice-service ──(after commit, background, 1s/2s timeouts)──► POST /internal/gamification/events
  DictationService / ShadowingService                                  (X-Internal-Token)
                                                                              │
vocabulary-service                                                            ▼
  ReviewController ── recordCardReview ──┐                     GamificationService
  GameProgressService.onRoundFinished ───┼── recordGameRound ──►  award(): INSERT … ON CONFLICT DO NOTHING
                                         │                        └─ if inserted → grant completed missions (+30 💎)
  GamificationController (/vocabs/gamification/*) ── me / check-in / leaderboard
```

**Ledgers, not counters.**

- Every XP gain is one `xp_event` row and every diamond change is one `diamond_event` row. Each row has `UNIQUE (source, ref_id)`.
- The `ref_id` holds the idempotency key and the anti-farming key. Writing the same award twice is a no-op, whether it comes from a duplicate event, a retried finish, or the same sentence on the same day.
- Totals, today's XP, weekly XP and the leaderboard are `SUM`s over indexed rows. There is no counter column, so nothing can drift.

**Days** are calendar days in `Asia/Ho_Chi_Minh` (`XpRules.ZONE`). Weeks run Monday to Sunday. For a practice event, the attempt's `occurredAt` decides the day. A game round counts on its `play_date`, which is also what the existing daily goal uses.

## 2. Tables (Flyway `vocabulary-service` V2, `V2__gamification.sql`)

| Table | Columns | Notes |
|---|---|---|
| `xp_event` | `id bigserial`, `user_id uuid`, `source varchar(32)`, `ref_id varchar(200)`, `amount int ≥ 0`, `day date`, `created_at` | `UNIQUE (source, ref_id)`. Indexes on `(user_id, day)` and `(day)`. |
| `diamond_event` | same shape; `amount` may be negative (future shop) | `UNIQUE (source, ref_id)`. Index on `(user_id, day)`. |
| `gamification_profile` | `user_id` PK, `display_name`, `checkin_streak`, `last_checkin_day`, timestamps | Locked (`FOR UPDATE`) during check-in. `display_name` is the masked e-mail (`kha***`, same rule as the game leaderboard), refreshed from the JWT on `/me`, check-in and leaderboard. |

`ref_id` keys:

| Source | `ref_id` | Effect |
|---|---|---|
| `DICTATION`, `DICTATION_PERFECT` | `user:sentence:day` | One base award and one bonus per sentence per day |
| `SHADOWING`, `SHADOWING_GOOD` | `user:sentence:day` | Same, as a separate mode |
| `CARD_REVIEW` | `user:userVocabId:day` | Once per card per day |
| `GAME` | game session id | Once per round |
| `GAME_LEGACY` | user id | Migration only |
| `CHECKIN`, `CHECKIN_STREAK_BONUS` | `user:day` | Once per day |
| `MISSION` | `user:day:MISSION_CODE` | Once per mission per day |

**Migration of the existing game XP (backfill in V2):**

- Every `FINISHED` `game_session` becomes a `GAME` row with `ref_id = session id`, which is the same key new rounds use. It is dated on its `play_date`, so past rounds also count in their own week.
- If `game_profile.xp` is greater than the sum of a user's rounds, the difference becomes one `GAME_LEGACY` row. On the live data the difference was 0 for all 14 profiles.
- Result: `SUM(xp_event.amount) = game_profile.xp` for every existing player. This was checked on the copy: 0 mismatches.
- `game_profile.xp` is still maintained and still means "game-only XP". The game's `Progress.xp` now reports the **shared** total, so the game screen shows the same level as the dashboard.
- `gamification_profile.display_name` is seeded from each player's latest round.

## 3. Endpoints

All public endpoints go through the gateway route `/vocabs/gamification` (`verifyToken` + `userLimiter`). `/internal/**` is never routed: the gateway returns 404, checked.

| Method / path | Body / params | Response |
|---|---|---|
| `GET /vocabs/gamification/me` | – | `GamificationMe {xp, level, levelXp, levelXpNeeded, todayXp, weekXp, diamonds, today, checkIn {checkedInToday, streak, lastCheckInDay, todayReward, daysToBonus}, missions [{code, icon, title, target, progress, completed, reward}]}` |
| `POST /vocabs/gamification/check-in` | – | `CheckInResult {alreadyCheckedIn, diamondsGained, bonusDiamonds, streak, diamonds}`. Always 200; a second call the same day returns `alreadyCheckedIn: true` and grants nothing. |
| `GET /vocabs/gamification/leaderboard?period=week` | `period` (only `week`; anything else → 400 `Chỉ hỗ trợ bảng xếp hạng XP theo tuần (period=week)`) | `XpLeaderboard {period, title "XP tuần dd/MM–dd/MM", from, to, entries [{rank, displayName, xp, me}] (top 20), me (also when outside top 20, null without XP), totalPlayers}`. Ranked by XP, then by who reached it first. |
| `POST /internal/gamification/events` (vocabulary-service, `X-Internal-Token`) | `{userId, type: DICTATION\|SHADOWING, attemptId, lessonId, sentenceId, accuracy, score, occurredAt}` | `EventResult {xpGained, day, awards [{source, amount, granted}]}`. 400 when `userId`, `type` or `sentenceId` is missing or `type` is unknown. 401 when the token is missing or wrong (shared `InternalTokenFilter`, now imported by vocabulary-service's `CommonConfig`). |

Existing endpoints that changed behaviour:

- `POST /vocabs/user/review` gives +2 XP once per card per day. It runs after the review commits, and a ledger error only logs a WARN.
- Finishing a game round records its score as XP.
- `GET /vocabs/games/me` and the finish summary now report the shared XP total in `progress.xp`/`level`.

## 4. Constants (`service/gamification/XpRules.java`, `Mission.java`)

| Rule | Value |
|---|---|
| Dictation sentence | +5 XP, +5 more when accuracy = 100 % (≥ 99.995) |
| Shadowing sentence | +5 XP, +5 more when score ≥ 85 |
| SRS card review | +2 XP |
| "Dịch nhanh" round | XP = round score (unchanged) |
| Anti-farming | a sentence gives XP once per mode per day, and its bonus once per mode per day. A card gives XP once per day. A round gives XP once. |
| Level | L → L+1 needs 100·L XP; level L starts at 50·L·(L−1) (unchanged formula, now in `XpRules.levelStart/levelFor`; `GameProgressService` delegates) |
| Daily check-in | +50 💎 |
| Check-in streak bonus | +100 💎 more on every 7th consecutive day (7, 14, 21 …). A missed day restarts the streak at 1. |
| Daily mission | +30 💎 each, 3 per day |
| Leaderboard size | 20 |

**Mission pool:**

| Code | Title | Counts |
|---|---|---|
| `DICTATION_5` | Làm 5 câu Dictation | 5 `DICTATION` rows today |
| `SHADOWING_3` | Shadowing 3 câu | 3 `SHADOWING` rows |
| `REVIEW_10` | Ôn 10 thẻ từ vựng | 10 `CARD_REVIEW` rows |
| `GAME_1` | Chơi 1 ván Dịch nhanh | 1 `GAME` row (a 0-point round counts) |
| `XP_50` | Đạt 50 XP hôm nay | today's XP sum ≥ 50 |
| `DICTATION_PERFECT_3` | Đúng 100% 3 câu Dictation | 3 `DICTATION_PERFECT` rows |

Because progress is read from the ledger, the anti-farming rules also apply to missions: repeating one sentence five times does not complete `DICTATION_5`.

## 5. Decisions

1. **Rewards are claimed automatically.** When an XP award makes a mission complete, its +30 💎 row is written in the same transaction. There is no claim button. `GET /me` also grants any completed mission that is missing its reward, so the state heals itself if something went wrong. The frontend toasts "+30 💎".
2. **Mission selection** is `Collections.shuffle(pool, new Random(seed(userId, date)))` and then the first 3, shown in pool order. `Random` and `shuffle` are fully specified by the JDK, so every node and every call returns the same pick. Nothing is stored; the reward rows record what was completed. If the pool changes, the current day's pick may change too. That is acceptable for a pool edit made at deploy time.
3. **The 7-day bonus** is +100 💎 on every multiple of 7 in the streak. This was my choice; the spec only says "e.g. day 7 gives a bonus".
4. **Check-in twice** returns 200 with `alreadyCheckedIn: true` instead of 409. The frontend disables the button anyway, and the call is idempotent.
5. **Practice events are fire-and-forget.** `GamificationClient` (practice-service) works like this:
   - It runs after commit (`TransactionSynchronization.afterCommit` for the `@Transactional` dictation submit), or immediately for shadowing, whose submit is not transactional.
   - It runs on a pool of 1–2 daemon threads with a queue of 500, with a 1 s connect timeout and a 2 s read timeout.
   - It never throws. A full queue or a vocabulary-service outage only logs WARN, so the event is lost. There is no retry: XP for that attempt is simply not granted.
   - It is switched off (one WARN at startup) when `VOCABULARY_SERVICE_URL` is blank.
   - It is wired into `DictationService`/`ShadowingService` with **setter injection** in one block marked `// --- P2-B gamification ---` at the end of each class, plus a single `notifyGamification(attempt); // P2-B` call after `saveAndFlush`. The constructors and the existing tests are untouched, so a merge with agent A's edits there should only conflict trivially.
6. **The XP rules live in vocabulary-service.** practice-service only reports facts (accuracy, score, sentence), and the amounts stay in one place.
7. **Review XP is in `ReviewController`**, not `ReviewService`. This keeps `ReviewService`'s constructor unchanged for the other agents and their tests.
8. The **frontend "+N XP" toast** compares two reads of `/me` (`gainMessages`). Practice XP arrives asynchronously, so practice screens refetch immediately and again after 1.5 s. Toasts happen in the topbar (`TopbarGamification`), which is mounted on every app page.
9. The only leaderboard period is **week**. A month board would be the same query with other bounds; it was not asked for.
10. **No level cap.** The spec backlog mentions "level tối đa 200" (≈ 2 M XP). I did not add a cap, which keeps the existing formula exactly as it is.

## 6. Frontend

- `lib/types.ts`, `lib/queryKeys.ts` (`qk.gamification`, `qk.xpLeaderboard(period)`): additions are in `// --- P2-B ---` blocks.
- `lib/endpoints/gamification.ts`: `gamificationApi.me / checkIn / leaderboard`.
- `lib/gamification.ts`: pure helpers (`levelPercent`, `missionPercent`, `missionsDone`, `checkInLabel/Hint`, `gainMessages`, the `XP_RULES` help table) and `lib/gamification.test.ts` (3 tests).
- `components/gamification/`:
  - `useGamification.ts`: the shared query, the gain toasts, `refreshGamification`, `useCheckIn`.
  - `GamificationCard.tsx`: on the dashboard, above the game widget, with `id="gamification"`. It shows the level bar, total, today and week XP, diamonds, the check-in button with the streak and bonus hint, the 3 missions with progress bars, an "Cách nhận XP" toggle, and a link to the XP board.
  - `TopbarGamification.tsx`: the 🏆 level and 💎 chip. The diamond count is hidden below 420 px; the chip links to `/dashboard#gamification`.
- `GameLeaderboard.tsx` has a new tab **"XP tuần"**. `/games?board=xp-week` opens it and scrolls to it; the URL is read on mount, so no Suspense is needed.
- Refresh hooks are one line each, marked `// P2-B`, in `DictationPractice`, `ShadowingPractice`, the review page, the deck study page and the games page.
- `navItems.ts` is unchanged. The board lives on the existing games page, and the topbar chip links to the dashboard card.

## 7. Verification evidence (2026-10-15, this machine)

**Java**

`scripts/verify-java.sh vocabulary` returned `verify-java: OK (vocabulary)`. Surefire: 103 tests, 0 failures/errors. The new and changed tests:

- `XpRulesTest` (7): amounts and bonus thresholds, anti-farming keys, level math, check-in streak and bonus, Vietnam day boundaries `16:59:59Z`/`17:00Z`, week start.
- `MissionTest` (3): deterministic pick, distinct missions, variation across days and users, the whole pool reachable, progress.
- `GamificationServiceTest` (8), against in-memory ledgers that enforce the unique key:
  - duplicate events and same-sentence-same-day get no XP, the bonus is still added later, there is XP again on the next Vietnam day, shadowing is a separate mode;
  - invalid events are rejected;
  - card review XP is given once per day across midnight in Vietnam;
  - a completed mission gives +30 💎 exactly once;
  - check-in is once per Vietnam day (23:59 vs 00:01), the streak reaches 7 with the +100 bonus, and a gap resets it;
  - the check-in state (reward and days to bonus);
  - `me` shows the level of the shared total;
  - period validation.
- `GameProgressServiceTest` (+1 `progressReportsTheAppWideXpTotal`, and it now verifies `recordGameRound` on finish).

`scripts/verify-java.sh practice` returned `verify-java: OK (practice)`, 51 tests. `GamificationClientTest` (4) covers: the payload and `X-Internal-Token`, sending only after commit inside a transaction, never throwing when the service is down or the queue is full, and being disabled without a URL.

**Frontend** (`frontend/`, deps from `npm ci` in `node:20-alpine`)

- `node_modules/.bin/tsc --noEmit`: OK.
- `node_modules/.bin/vitest run`: 20 files, **186 passed**.
- `node_modules/.bin/next lint`: `✔ No ESLint warnings or errors`.
- `next build`: exit 0 (`/dashboard` 12.1 kB, `/games` 13.7 kB).

**Gateway and compose**

- `node --check api-gateway/server.js`: OK.
- `docker-compose --env-file .env.example config -q`: OK.

**Runtime (throwaway)**

Setup:

1. `docker-compose --env-file .env.example -p p2b -f docker-compose.yml build vocabulary-service practice-service`.
2. A separate `postgres:16-alpine` (`p2b-pg`) and `redis:7-alpine` on network `p2b-net`.
3. `pg_dump --no-owner --no-privileges vocabulary_db|practice_db` from `parroto-postgres` loaded into the copy: 27 sessions, Σ game XP 2049, the same as live.
4. New images on ports 127.0.0.1:18004 and 18003. practice-service was also attached to the live network to read lessons from `parroto-content-service` with GET only.
5. Tokens were minted with the throwaway `JWT_SECRET`.

Results:

1. **Flyway:** `Creating Schema History table … with baseline`, then `Successfully baselined schema with version: 1`, then `Migrating schema "public" to version "2 - gamification"`. `flyway_schema_history` shows `1 BASELINE`, `2 SQL gamification`, both successful. A restart logged `Current version … 2 … up to date`.
2. **Existing game XP preserved:** 0 users where `game_profile.xp ≠ Σ xp_event`. `xp_event` has `GAME | 24 rows | 2049`. For user `c2b2e3ef…`, `/vocabs/gamification/me` shows `xp 390, level 3, levelXp 90/300`, and `/vocabs/games/me` progress shows `xp 390, level 3`.
3. **A dictation attempt produces XP through the internal event:** `POST /practice/dictation/attempt` at 100 % moved `/me` from `xp 400` to `todayXp 10`, i.e. +5 and +5 perfect.
4. **Duplicates are ignored:**
   - The same sentence again changed nothing (xp stayed 400).
   - Replaying the event directly on `/internal/gamification/events` returned `{"xpGained":0,"awards":[{"source":"DICTATION","amount":5,"granted":false},{"source":"DICTATION_PERFECT","amount":5,"granted":false}]}`.
   - Without a token the call returned 401; with a wrong token it returned 401 `Invalid or missing internal token`.
5. **Mission gives diamonds:** 4 more sentences gave `xp 440, todayXp 50, diamonds 30` and `DICTATION_5 5/5 completed`, with one `MISSION … :2026-10-15:DICTATION_5 | 30` row. A 0-point game round (start, skip, finish ×2) completed `GAME_1` (+30). The finish summary showed `progress.xp 440`.
6. **Check-in once per day:** the first call returned `{"alreadyCheckedIn":false,"diamondsGained":50,"streak":1,"diamonds":80}`, the second `{"alreadyCheckedIn":true,"diamondsGained":0,…,"diamonds":80}`. The final `/me` showed `diamonds 110`, `checkIn {checkedInToday: true, streak: 1, daysToBonus: 6}`.
7. **Card review:** for a user with notebook cards, two reviews of the same card took `/me` xp from 0 to 2 (once per day).
8. **Leaderboard:** `XP tuần 12/10–18/10 total 11`, with me at rank 1 `dem*** 440` followed by `toe*** 325` and `e2e*** 290`. `period=year` returned 400.
9. **vocabulary-service down:** with `p2b-vocab` stopped, a dictation attempt returned **201 in 0.024 s**, and practice logged `WARN … GamificationClient : Gamification event for attempt … not delivered (vocabulary-service): I/O error …`.
10. **Gateway:** I ran the worktree gateway in `node:20-alpine` against the copy. `GET /vocabs/gamification/me` returned 401 without a token and 200 with one. `POST /internal/gamification/events` and `/vocabs/internal/...` both returned **404**.
11. **Cleanup:**
    - Removed the containers `p2b-gw`, `p2b-practice`, `p2b-vocab`, `p2b-redis` and `p2b-pg`, the network `p2b-net`, and the `p2b-*` images.
    - Ran `docker volume prune -f` for the anonymous volumes. This removes every unused anonymous volume on the daemon, not only mine.
    - The live stack (9 `parroto-*` containers) kept running. The live `vocabulary_db` still has no `flyway_schema_history` or `xp_event`.

## 8. Risks / follow-ups

- **Lost events:** XP for an attempt is lost if vocabulary-service is down, the queue is full, or practice-service restarts while an event is queued. There is no retry and no outbox, by design ("never fail or slow the attempt"). If XP must be exact, add an outbox table in practice-service, or a nightly reconcile job (`practice_attempt` → events; idempotent, so it is safe to replay).
- **First deploy:** vocabulary-service baselines and then runs V2, including the backfill, on the live volume. This was proven on a copy. The backfill is a single `INSERT … SELECT` over `game_session` (27 rows today).
- **Merging with agent C (also in vocabulary-service):**
  - V3 is theirs, and my V2 must be merged first. Flyway `outOfOrder=false` refuses V3 before V2.
  - Shared files I touched: `CommonConfig` (adds `InternalTokenFilter`) and `SecurityConfig` (`/internal/**` permit + filter). vocabulary-service now **requires `INTERNAL_TOKEN` at startup through the filter**; compose already required it.
  - Also touched: `ReviewController` (constructor plus the XP call), `GameProgressService` (new constructor argument; `GameProgressServiceTest` updated).
- **Merging with agent A (practice-service):** expect trivial conflicts near `saveAndFlush` and at the end of `DictationService`/`ShadowingService`. Keep both blocks.
- **Shadowing near midnight:** the event day is the attempt's `createdAt` (JVM time with offset), converted to Vietnam time. For shadowing, if an Azure call takes long around midnight, the attempt counts on the day it was saved.
- **`displayName`** is only known for users who opened an authenticated gamification or game endpoint. Users who only practise would show as "Người chơi". In practice the topbar calls `/me` on every page, so their names fill in quickly.
- **Topbar cost:** the topbar adds one `GET /vocabs/gamification/me` per page load. It runs about 6 indexed queries plus one no-op upsert, so the cost should be small, but it was not measured.
- **Pre-existing, not changed, not tested:** gateway prefix mounts forward `req.originalUrl` as it is. A crafted dot-segment path under an allowed prefix might reach a service's `/internal/**`. If it does, `InternalTokenFilter` still requires the secret token. This applies to every prefix, not just the new one.
