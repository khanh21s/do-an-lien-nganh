# P2-anki: Anki-style flashcards (scheduler, sessions, migration)

Branch `feat/phase2-anki`, based on `feat/phase2` at `4e11ef0`. Vocabulary flashcards now work like
the Anki app. Before this branch every new card showed "1 ngày" on all four buttons (plain SM-2).
The same scheduler now drives deck study, the global "Ôn tập" page and the notebook. Sentence review
("Ôn câu sai", practice-service) is unchanged.

## 1. User decisions applied

1. The deck page is an Anki **session**, not a browse of all cards. Order: due learning cards, then
   due review cards of the deck, then new cards (never graded, random order). A card answered "Học
   lại" comes back in the same session when its step is due. Counters **Mới / Đang học / Ôn**
   (blue / red / green). A **"Đã xong hôm nay"** screen shows when the next card is due. The group
   filter (`groupId`) and typing mode still work.
2. **No daily limits** for new or review cards. The "Thẻ/ngày" input is gone.
3. **One scheduler for every vocabulary card**: deck study, `/vocabulary/review` (all decks) and
   the notebook. A word added to the notebook is a NEW card and shows up as new in "Ôn tập".

## 2. Scheduler (`vocabulary-service` `service/srs/`)

| Class | Role |
|---|---|
| `AnkiDefaults` | Every constant, documented in one place (table below). |
| `AnkiScheduler` | Pure function `answer(card, rating, now)` plus `previews(card, now)` (the four outcomes) and `grade(UserVocab, rating, now)` (applies an outcome to the entity). |
| `SrsCard` | State snapshot: `state, step, intervalDays, ease, lapses, due`. |
| `AnkiDay` | Anki days: a day starts at 04:00 Asia/Ho_Chi_Minh. |
| `IntervalLabels` | Vietnamese labels: `<1 phút`, `6 phút`, `1,5 giờ`, `4 ngày`, `1,2 tháng`, `2,2 năm`. |
| `Rating` | `AGAIN=1, HARD=2, GOOD=3, EASY=4`. Old SM-2 qualities map as 0–2 → Again, 3 → Hard, 4 → Good, 5 → Easy. |

**Constants (Anki defaults):**

| Setting | Value |
|---|---|
| Learning steps | 1m, 10m |
| Graduating interval | 1 day |
| Easy interval | 4 days |
| Starting ease | 2.50 |
| Easy bonus | 1.30 |
| Hard interval | ×1.20 |
| Interval modifier | 1.00 |
| Relearning steps | 10m |
| New interval after a lapse | 0 % of the old interval, minimum 1 day |
| Ease floor | 1.30 |
| Maximum interval | 36,500 days |
| Ease changes | Again −0.20, Hard −0.15, Easy +0.15 (review cards only) |
| Next day starts at | 04:00 Vietnam time |
| Learn-ahead limit | 20 minutes |
| Fuzz | off |

**Transitions:**

- **NEW / LEARNING** (step i):
  - Again → step 0 (1m).
  - Hard → stays on step i. On the first step the delay is the average of steps 1 and 2 (5.5m, shown as "6 phút").
  - Good → step i+1. After the last step the card graduates to REVIEW with 1 day.
  - Easy → graduates at once with 4 days.
  - A NEW card gets ease 2.50. Answers during learning never change the ease.
- **REVIEW** (interval I, ease E, days late d ≥ 0):
  - Again → lapse: E −0.20, lapses +1, RELEARNING step 0 (10m). The interval kept for later is max(1, I×0).
  - Hard → I×1.2, at least I+1; E −0.15.
  - Good → (I + d/2)×E, at least Hard+1.
  - Easy → (I + d)×E×1.3, at least Good+1; E +0.15.
- **RELEARNING**:
  - Again → step 0.
  - Hard → repeats the step. With a single step this is ×1.5, so 15 minutes (Anki's rule).
  - Good → back to REVIEW with the kept interval.
  - Easy → back to REVIEW with the kept interval + 1 day (Anki).
- **Days and timestamps**:
  - Intervals are rounded half-up and clamped to [1, 36500] days.
  - A review card is due at 04:00 (VN) of its due day. Learning steps use exact timestamps.
  - Days late are counted in Anki days.
- **Early review**: a review card answered before its due day uses Anki's early-review formulas,
  based on the days that actually elapsed. Sessions only show due cards, so this happens only
  through the "Dịch nhanh" game or a direct API call. It stops anyone from inflating intervals by
  grading cards early.

Examples:

- A new card shows `<1 phút / 6 phút / 10 phút / 4 ngày`.
- A review card with I=10 and E=2.50, due today, shows `10 phút / 12 ngày / 25 ngày / 1,1 tháng` (33 days).
- The same card 4 days late: Good gives 30 days, Easy gives 46 days.

## 3. Data (`V4__anki_scheduling.sql`)

**New columns on `user_vocab`:**

- `state varchar(16) NOT NULL DEFAULT 'NEW'`, with a CHECK on the four states.
- `step int NOT NULL DEFAULT 0`.
- `lapses int NOT NULL DEFAULT 0`.
- Index `(user_id, state, next_review_at)`.

**Existing columns:**

- `next_review_at` is now NOT NULL.
- Defaults: `interval_days` 0, `ease_factor` 2.50.
- `repetitions` now counts every answer (Anki "reviews"). Before, it was SM-2's success streak.
- `quality` still stores the last answer as 1/3/4/5, so the notebook's accuracy still works.

**Mapping of existing rows:**

| Old row | New state | Values |
|---|---|---|
| `repetitions > 0` | REVIEW | Interval clamped to [1, 36500] and ease to ≥ 1.30 (both unchanged on the live copy). `next_review_at` moves to 04:00 VN of the Anki day it fell on, so a card due "later today" is due from this morning, as in Anki. |
| `last_review_at IS NULL` (never answered: notebook words, "add to notebook") | NEW | interval 0, ease 2.50 |
| Answered but `repetitions = 0` (last answer was "Again") | RELEARNING step 0 | Due at its old `next_review_at`. The kept interval is 1 day, the ease is kept, and lapses start at 0 because the count is unknown. |

Hibernate `validate` passes: the service starts on both a fresh volume and the live copy.

## 4. Endpoints (gateway paths)

| Method and path | Notes |
|---|---|
| `GET /vocabs/decks/{id}/study?groupId=<uuid\|none>&seed=` | Deck session. Returns `{counts {new, learning, review}, card, nextDueAt, now}`. `card` has `{userVocabId\|null, cardId, deckId, groupId, word, meaning, example, exampleVi, ipa, partOfSpeech, audioUrl, imageUrl, state, queue (new\|learning\|review), intervalDays, easeFactor, lapses, repetitions, nextReviewAt, previews[4] {rating, label, state, intervalDays, nextReviewAt}}`. **Login required** (401 for guests). Pro deck → 403 for free users; someone else's personal deck → 404; a bad `groupId` → 400. |
| `GET /vocabs/user/study?seed=` | Global session over all of the user's rows. New cards are the NEW rows, in the order they were added. |
| `POST /vocabs/user/review` | Body `{userVocabId \| cardId, rating 1–4, answeredAt?, deckId?, groupId?, seed?}`. The legacy `{userVocabId, quality 0–5}` still works. A `cardId` without a row creates the row (`INSERT … ON CONFLICT DO NOTHING`, then `SELECT … FOR UPDATE`, so concurrent first answers make one row). Pro card for a free user → 403; foreign personal card → 404. Returns `{userVocabId, cardId, rating, state, step, intervalDays, interval, easeFactor, repetitions, lapses, nextReviewAt, label, message, session}`. `session` is the refreshed session of the scope: the deck when `deckId` is given, otherwise global. The page needs one request per answer. +2 XP once per card per day (unchanged, `ReviewController`). |
| `GET /vocabs/user/stats` | `dueToday` = LEARNING, RELEARNING and REVIEW cards due before the next 04:00 (the nav badge). `dueThisWeek` uses the same rule, 6 days further. `bySRS` = NEW / learning+relearning / REVIEW. |
| `GET /vocabs/user/learned` | Adds `state`, `lapses` and `intervalLabel` (server label, null while new or learning). |
| `GET /vocabs/user/review-queue` | Kept (legacy). Lists due learning (learn-ahead included) and due review cards, never NEW. Items have `state`. |
| `POST /vocabs/user/add-from-lesson`, `POST /vocabs/user/words`, notebook import | Create NEW cards. |

**The game ("Dịch nhanh")** grades notebook words with the same scheduler: miss → Again, typo or hint → Hard, clean → Good. As before, a miss always counts and a correct answer only counts when the card is due (now day-based for review cards).

## 5. Frontend

- `components/vocab/study/StudySession.tsx`:
  - `useStudySession` holds the seed, the query and the grade mutation. The grade response replaces the cached session, and the card key changes per answer because a learning card can follow itself.
  - The session refetches automatically when the next card falls due within an hour.
  - `StudySessionPanel` is the shared UI: counters, `StudyCard` (typing mode included), the four buttons with the server's labels, and the done screen.
  - `StudyCounters`: Mới blue, Đang học red, Ôn green. The current card's counter is underlined.
  - `StudyDone`: "Đã xong hôm nay!", the answer distribution and "Thẻ tiếp theo đến hạn ngày mai lúc 04:00".
- `vocabulary/decks/[id]`:
  - The session replaces the sequential browse. Group chips, the Pro upgrade card and the "deck has no cards" message are kept.
  - Grading a card that was never studied sends `cardId`. The old add-from-lesson-then-review round trip is gone.
- `vocabulary/review`: the global session. The daily-limit input is removed, and an empty notebook gets its own message.
- **Notebook**: badges Thẻ mới / Đang học / Chu kỳ `<server label>`. A new word reads "Chưa học – có trong phiên Ôn tập". The add toast says the word is in "Ôn tập".
- `RatingButtons` and `StudyCard` take `previews` (Học lại / Khó / Tốt / Dễ, keys 1–4, suggestion in typing mode).
- `lib/srs.ts` no longer copies the formula. It keeps the button names and keys, `previewLabel`, `formatNextDue`, the typing helpers and the distribution.
- `DailyLimitInput`, `ReviewSummary` and `storageKeys.reviewDailyLimit` are deleted.
- `lib/types.ts`, `lib/queryKeys.ts` and `lib/endpoints/vocabulary.ts` have `// --- P2-anki ---` blocks:
  - `qk.studySession` sits under `review-queue` and `qk.deckStudy` under `deck-cards`, so existing invalidations refresh them.

## 6. Decisions I made (beyond the brief)

1. **No fuzz.** Anki adds a small random spread to intervals ≥ 3 days. Leaving it out keeps the labels stable ("4 ngày" for Easy on a new card) and the tests exact. To add it later, make it deterministic per card and day inside `AnkiScheduler.constrain`; the previews will still match because they call the same code.
2. **Learn ahead**:
   - The "Đang học" counter counts learning cards due within 20 minutes, as Anki does.
   - With steps 1m/10m (and 15m for Hard while relearning), no learning card ever waits longer than 20 minutes. So "Đã xong hôm nay" appears only when every learning card has graduated, or when the user left one and comes back later.
3. **Order of new cards**:
   - Deck: random, fixed per visit by `seed`. The seed is regenerated on every visit and group change.
   - Global session: order added (Anki's default "order added"). That fits words collected from lessons.
4. **Labels**: one minute or less reads "<1 phút" (as in the brief's example). Other minute steps are rounded ("6 phút"). Months are 365/12 days; one decimal with a comma, and a trailing ",0" is dropped.
5. **`answeredAt`** is used only when it lies within the last 10 minutes and is not in the future; otherwise the server time is used. Nothing in the UI sends it yet.
6. **Session scope in the grade request**: the response carries the next card, so the UI makes one request per answer. A Pro or foreign deck scope is checked before anything is written.
7. **dueToday no longer counts never-studied notebook words.** Before this branch they were "due now" and showed in the nav badge. Now they are new cards (blue counter in "Ôn tập") and not "due", as in Anki.
8. **Undo** was not implemented (optional in the brief).
9. **Two different day boundaries.** Gamification days (XP, "Ôn 10 thẻ") still change at midnight Vietnam time. SRS days change at 04:00. The mission rules are unchanged.

## 7. Verification (2026-10-15, this machine)

- **Java**: `scripts/verify-java.sh vocabulary` → `verify-java: OK (vocabulary)`. Surefire: **221 tests**, 0 failures or errors (`SM2CalculatorTest` was removed with `SM2Calculator`). New tests:
  - **`AnkiSchedulerTest` (53)**, fixed clock 2026-10-15 10:00 +07:
    - every transition: new, learning, review, relearning; Hard = 5.5m on the first step; graduation; Easy 4 days; lapse; relearning Easy +1 day;
    - the 04:00 VN boundary: `20:59:59Z` vs `21:00Z`; a review card due at 04:00; graduating at 03:59 counts for the previous day; days late in Anki days;
    - monotonic Hard < Good < Easy, parameterised, including the 36,500-day cap;
    - ease floor 1.30; early review;
    - previews equal the actual grade result for 7 different cards × 4 ratings;
    - labels, ratings and legacy qualities.
  - **`V4MigrationContractTest` (3)**: the mapping CASE and the 04:00 normalisation in the SQL, and how migrated REVIEW, RELEARNING and NEW cards then behave.
  - **`StudyServiceTest` (16)**, repositories mocked:
    - session order learning-due → review → new → learn-ahead, and the done screen's `nextDueAt`;
    - counts, group modes, the global session;
    - Pro → 403, foreign personal deck → 404, guest → 401;
    - first grade creates the row and matches the preview; Good, Good → 1 day; Again on review → relearning;
    - legacy quality, validation, a Pro scope checked before writing, the `answeredAt` window.
  - Updated: `ReviewServiceTest`, `GameServiceTest`, `GameScoringTest`, `PersonalWordServiceTest`, `LearnedWordResponseTest`.
- **Frontend** (`frontend/`, `TMPDIR=$HOME/.cache/...`):
  - `node_modules/.bin/tsc --noEmit` → OK.
  - `node_modules/.bin/vitest run` → 25 files, **207 tests** passed.
  - `node_modules/.bin/next lint` → `✔ No ESLint warnings or errors`.
  - `node_modules/.bin/next build` → compiled; `/vocabulary/decks/[id]` 3.04 kB, `/vocabulary/review` 846 B.
- **Runtime on a throwaway stack.** Setup:
  - An untracked copy of the compose file with `container_name: p2anki-*` and ports 127.0.0.1:18000 / 13000, run as `docker-compose -p p2anki -f docker-compose.p2anki.yml --env-file .env` with `FRONTEND_URL=http://localhost:13000 NEXT_PUBLIC_API_URL=http://localhost:18000`.
  - The 7 images were built from this branch.
- **Runtime results:**
  1. **Fresh volume.** `flyway_schema_history`: `1 BASELINE`, `2 gamification`, `3 vocab groups personal decks`, `4 anki scheduling`, all successful.
  2. **Copy of the live data.**
     - Setup: `pg_dump --no-owner --no-privileges vocabulary_db` from `parroto-postgres`, loaded into the throwaway `vocabulary_db` (already at V3). Rows before: 124 user_vocab rows, 20 users. 14 with `repetitions > 0`, 16 never reviewed, 94 reviewed with `repetitions = 0`. 37 rows were due.
     - Flyway: `Current version … 3` → `Migrating schema "public" to version "4 - anki scheduling"` → started (validate OK).
     - Rows after: **REVIEW 14, NEW 16, RELEARNING 94**.
     - Compared with a before-snapshot table:
       - REVIEW: all 14 kept interval and ease; all 14 now due at 04:00 of the same Anki day.
       - RELEARNING: all 94 kept due time and ease.
       - NEW: due time kept; interval 1 → 0.
       - Every row kept `repetitions`.
     - The largest user (93 rows, 15 due before): `/vocabs/user/stats` → `dueToday 15`, `bySRS {new 0, learning 91, review 2}`. `/vocabs/user/study` → counters `{new 0, learning 13, review 2}`, first card RELEARNING with `10 phút / 15 phút / 1 ngày / 2 ngày`.
     - A restart logged `Schema "public" is up to date`.
  3. **curl session through the gateway** (new user, B2 deck):
     - Study → `{new 734, learning 0, review 0}`, a NEW card `<1 phút / 6 phút / 10 phút / 4 ngày`.
     - Good with `cardId` → LEARNING step 1, `10 phút`, counters `{733, 1, 0}`.
     - Good → REVIEW, interval 1, `1 ngày`, due `2026-10-16T04:00+07:00`.
     - Made due by SQL with interval 10, one day late → it was the next card, `queue review`, `10 phút / 12 ngày / 26 ngày / 1,2 tháng`.
     - Again → RELEARNING, lapses 1, ease 2.3, `10 phút` (= its preview). Good → REVIEW 1 day.
     - Legacy `quality: 4` → 200 (rating 3). `rating: 7` → 422.
     - Notebook word → `state NEW`; the global session then showed it as the new card with the new-card labels.
     - Pro deck study as a free user → **403** "This vocabulary deck requires a Premium subscription". Grading a Pro card → **403** "Bộ từ Pro – nâng cấp Premium để học". Guest study → 401. Foreign personal deck → 404. `groupId=none` → 200; `groupId=bad` → 400.
     - XP: one `CARD_REVIEW` row (+2) for 6 answers to the same card on one day.
     - Three parallel first answers to one card: 200 ×3, **one** row, repetitions 3.
  4. **E2E** (Playwright 1.47.2 image, `BASE_URL=http://localhost:13000 API_URL=http://localhost:18000`, the main checkout's `e2e/node_modules` mounted read-only):
     - `anki-session`, `custom-shortcuts`, `deck-typing-retry`, `t13-notebook-add` (×2): **5 passed**.
     - `phase1` 01–05 passed. Then the runner's SQL (`next_review_at = now() - 1h` for that user, run on `p2anki-postgres`), then 05b, 06 and 07 passed (07 with `ADMIN_PASSWORD` from `.env`).
     - 08 first failed. The review reminder ("Đã đến lúc ôn tập: 2 từ") covered the tab bar, because 05b now leaves two cards in a learning step, so they are due today. The spec now closes the reminder; 08 and 09 then passed.
  5. **Cleanup**:
     - `docker-compose -p p2anki … down -v` removed the containers, the network and the 3 project volumes. The 7 `p2anki-*` images were removed.
     - The temporary compose file and e2e outputs were deleted.
     - `docker volume prune` was **not** used.
     - The live `parroto-*` stack (9 containers) was never stopped or written to. I only ran `pg_dump` and SELECTs on it. Its `vocabulary_db` is still at Flyway V3.

## 8. Risks and follow-ups

- **First live deploy**: V4 rewrites `user_vocab`, one UPDATE per state (124 rows today). REVIEW due times move earlier to 04:00 of their day. This was proven on a copy of the live data.
- **Notebook badge change**: never-studied notebook words no longer make the nav badge red. They are blue "Mới" in "Ôn tập". This follows the brief ("due learning + review").
- **Learning cards left mid-session** count as due today. The dashboard review reminder can open for them, which is correct, and that is why the phase1 08 spec changed.
- **Merging**: `ReviewService`'s constructor changed (`AnkiScheduler` replaces `SM2Calculator`), and `GameScoring.srsQuality` became `srsRating`. Other branches that call these need the new names. Vocabulary migrations: the next free version is **V5**.
