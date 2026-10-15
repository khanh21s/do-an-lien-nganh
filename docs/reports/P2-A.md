# P2-A: Premium (Pro) lessons + sentence review SRS ("Ôn câu sai")

Branch `feat/phase2-a-lessons` (based on `feat/phase2` after the P2 foundation merge). Services touched:
content-service (migration `V2`), practice-service (migration `V2`), frontend, `docker-compose.yml`.
Nothing in vocabulary-service, subscription-service, auth-service or the gateway changed.

## A1 — Pro lessons ("preview, lock practice")

### Data and content-service
- `V2__lesson_is_pro.sql`: `lesson.is_pro boolean NOT NULL DEFAULT false`. All existing lessons stay free.
- `Lesson.isPro`. `LessonSummaryResponse` and `LessonDetailResponse` both have `isPro: boolean`. The JSON
  name `isPro` is pinned by a test.
- Admin `POST /content/lessons` and `PUT /content/lessons/{id}` accept an optional `isPro`. On create,
  null means false. On update, null leaves the flag unchanged.
- `GET /content/lessons?isPro=true|false` filters the list (omit it to get every lesson). Unfiltered
  cache keys keep their old format `lessons:list:{cefr}:{topic}:{page}:{limit}`. A filtered key adds
  `:pro=<bool>`, so it still falls under the existing `lessons:list:*` eviction. Eviction on update is
  unchanged (`lesson:{id}` + `lessons:list:*`, after commit).
- Search results include `isPro` too, because they use the same DTO.

### Enforcement (practice-service, server side)
- `LessonAccessGuard.requirePracticeAccess(user, lesson)` runs on:
  - `POST /practice/dictation/attempt`
  - `POST /practice/shadowing/attempt`
  - `POST /practice/shadowing/request-upload-url`

  The gate reads `isPro` from the content-service lesson detail that practice already fetches. The
  `ContentServiceClient` now returns a typed `LessonRef`/`SentenceRef` instead of only the text, so
  the gate adds no extra HTTP call.
- Outcomes:
  - Free lesson, or admin (`ADMIN_EMAILS`, shared `AdminGuard`): the attempt goes through.
  - Pro lesson, user not premium: **403**
    `"Bài học này dành cho tài khoản Premium. Nâng cấp Premium để luyện Dictation và Shadowing."`
  - Premium status unknown because subscription-service is unreachable: **503**
    `"Không kiểm tra được gói Premium, vui lòng thử lại sau."`. It still fails closed, but it does not
    tell a paying user to upgrade. vocabulary-service would answer 403 here instead.
- `PremiumClient` (practice) follows the same pattern as vocabulary's: `GET {SUBSCRIPTION_SERVICE_URL}/internal/subscriptions/{userId}/active`
  with `X-Internal-Token`, cached in the **same Redis key `premium:{userId}`** (JSON boolean, 5 min)
  through the shared `CacheService`. Failures are not cached. subscription-service's existing eviction
  on purchase/activation therefore unlocks Pro lessons immediately (proven below).
- `docker-compose.yml`: practice-service gets `SUBSCRIPTION_SERVICE_URL` and `ADMIN_EMAILS`.
  `INTERNAL_TOKEN` was already set. `application.yml` gets `services.subscription-service-url` and `admin.emails`.

### Frontend
- A `👑 Pro` badge (`ProBadge`) appears on `LessonCard`, on the lesson page header, in the admin list
  and on the admin lesson page.
- Lesson page: the transcript and audio stay available to everyone. When the lesson is locked for the
  user (or the visitor is a guest), an amber banner with "Nâng cấp Premium" is shown, and the two
  practice buttons show `🔒 … / Dành cho Premium`. The buttons still link to the practice routes.
- `/lessons/[id]/dictation/[sentenceId]` and `/lessons/[id]/shadowing/[sentenceId]` render
  `ProLessonLock` instead of the practice screen. `ProLessonLock` reuses `UpgradeCard`, which got
  optional `title/backHref/backLabel` props; the deck wording stays the default.
  - Lock rule: `lib/pro-lesson.ts#isLessonLocked`, which uses `userSubscription.isPremium` and
    `profile.isAdmin` from the shared query caches.
  - Unknown premium status means "not locked", and the server decides.
  - `DictationPractice` / `ShadowingPractice` also switch to the lock card on a 403 from practice-service,
    for example when premium expired while the page was open.
- Admin: the create modal and `LessonInfoForm` have a "Chỉ dành cho Premium" checkbox.

## A2 — Sentence review with SRS

### Table (`V2__sentence_review.sql`)
`sentence_review` has these columns:

| Group | Columns |
|---|---|
| Identity | `id`, `user_id`, `sentence_id`, `lesson_id` |
| Mode | `mode` (CHECK dictation/shadowing) |
| SM-2 state | `repetitions`, `interval_days`, `ease_factor numeric(4,2) default 2.50`, `next_review_at` |
| Last attempt | `last_score`, `last_reviewed_at` |
| Timestamps | `created_at`, `updated_at` |

Constraints and indexes: `UNIQUE (user_id, sentence_id, mode)` and index `(user_id, next_review_at)`.
A sentence can be queued once per mode.

### Rules (`ReviewRules`, `SentenceReviewService.recordAttempt`)
These rules run on every dictation/shadowing attempt. `result` is the Dictation **accuracy** or the
Shadowing **score**.

1. **The attempt comes from the review screen (`reviewId` = the caller's own item for the same sentence + mode)**:
   - If the item is due, it is graded with SM-2. Quality mapping:
     - Dictation: 100 → 4, ≥ 80 → 3, else 1.
     - Shadowing: ≥ 85 → 4, ≥ 70 → 3, else 1.
   - A failed review is SM-2 "Again": repetitions 0, due in 1 day. It is *not* reset to "now", which
     would loop the item forever.
   - If the item is not due (it was already graded, e.g. a retry right after the graded try), nothing
     changes (`unchanged`).
   - The row is locked with `PESSIMISTIC_WRITE` while grading.
2. **Any other attempt that fails** (Dictation accuracy < 100, Shadowing score < 70): an atomic
   `INSERT … ON CONFLICT DO UPDATE` adds the item or resets it. Reset means repetitions 0, interval 0,
   ease factor kept, **due now**.
3. **Any other passing attempt** does not touch the queue.
4. An unknown, foreign or mismatching `reviewId` is ignored, and rules 2 and 3 apply. The attempt
   itself is never rejected because of it.

The SM-2 step is an exact copy of vocabulary-service's `SM2Calculator` (architecture §4.6) in
`service/review/SentenceSm2`, so there is no dependency on vocabulary-service. The next review is
`now + interval days`.

### Endpoints (gateway `/practice/**`, Bearer required)

| Endpoint | Response |
|---|---|
| `GET /practice/review/queue?limit=` (1–100, default 20; otherwise 400 `limit phải từ 1 đến 100`) | `SentenceReviewItem[]`, due items with the oldest due first. Each item has `{id, lessonId, sentenceId, mode, repetitions, intervalDays, easeFactor, nextReviewAt, lastScore, lastReviewedAt, createdAt, lessonTitle, lessonAudioUrl, lessonIsPro, locked, sentence}`. `sentence` mirrors content's `SentenceResponse`. `locked` = Pro lesson and the user is not premium. The service fetches content once per lesson. Items whose lesson or sentence is gone (content 404) are deleted from the queue. |
| `GET /practice/review/stats` | `{dueCount, total, nextDueAt}`. `nextDueAt` is the earliest future due time, or null. |
| `DELETE /practice/review/{id}` | `{message:"Đã xoá câu khỏi hàng ôn tập"}`. If the item is not the caller's, 404. |
| `POST /practice/dictation/attempt`, `POST /practice/shadowing/attempt` | The body takes an optional `reviewId`. The response gets `review: {reviewId, action:'queued'|'graded'|'unchanged', quality, repetitions, intervalDays, easeFactor, nextReviewAt} \| null`. |

### Frontend
- `/practice/review` ("Ôn câu sai"):
  - The page snapshots the queue (up to 50 items) and walks it with the **existing**
    `DictationPractice` / `ShadowingPractice`.
  - Both components take a new optional `review` prop (`components/practice/review-mode.ts`). It
    carries `reviewId`, a header renderer, `isLast/onNext` and `onResult`.
  - In review mode the lesson-nav hooks see a one-sentence lesson (`lib/sentence-review.ts#reviewLesson`),
    so there is no prev/next.
  - After each attempt the page shows "Đã chấm “Tốt” · lần ôn tiếp: sau 6 ngày".
  - Other screen features:
    - "Xoá khỏi hàng ôn" removes the current item.
    - Locked (Pro) items show the upgrade card with "Bỏ qua".
    - Finishing the queue shows a summary.
    - An empty queue shows an empty state with the next due time.
  - Dictation keeps the stored difficulty.
- Navigation: `NAV_SENTENCE_REVIEW` (`🔁 Ôn câu sai`, badge `sentenceReviewDue` = `stats.dueCount`) sits
  after "Bài học". On mobile it is in the "Thêm" drawer, and the 4 tabs are unchanged.
- Dashboard: `SentenceReviewCard` (due count, total, next due, "Ôn ngay →").
- Every dictation/shadowing attempt invalidates `qk.sentenceReviewStats` / `qk.sentenceReviewQueue`.
- Shared files keep the additions in `// --- P2-A ---` blocks:
  - `lib/types.ts` uses **interface declaration merging**, so the existing interfaces get `isPro`,
    `reviewId` and `review` without editing their lines.
  - `lib/queryKeys.ts` and `lib/endpoints/practice.ts` have their own blocks.
  - `navItems.ts` is the exception: it needed 3 one-line edits outside its block. These are the
    `NavBadge` union, the `MAIN_NAV` entry and the `badgeCount` 3rd argument.

## Decisions I made (please review)
1. **Failed attempts are due *now*** (not tomorrow). Today's mistakes show up in "Ôn câu sai"
   right away. Spacing starts with the first graded review.
2. **Normal passing attempts are not reviews.** In normal practice the learner just saw the
   corrections (wrong-word diff / "Xem đáp án"), so a pass right after a fail says little about
   recall. Only the review screen grades. A failed normal attempt on a scheduled item does reset it.
3. **Retries on the review screen don't reschedule.** Only the attempt on a *due* item grades it, so
   a retry after a failed review (now due tomorrow) is just a normal attempt record. This rule does
   not apply to rule 2: a failing retry *without* `reviewId` would reset the item to now, so the FE
   always sends `reviewId` in review mode.
4. **Items are never auto-graduated.** They stay in the queue with growing intervals until the user
   deletes them, like vocabulary SRS. Orphans are removed when content-service says the lesson or
   sentence no longer exists.
5. **Premium check down → 503, not 403** (see A1).
6. Locked (Pro, not premium) items stay in the queue and count in `dueCount`. The review page shows
   the upgrade card for them.

## Verification (2026-10-15, this machine)

### Java
- `scripts/verify-java.sh content` → `verify-java: OK (content)`. 35 tests, 0 failures, including the
  new `LessonServiceProTest` (4): create default/Pro, update toggle + eviction, list filter + cache
  key, JSON name `isPro` + round trip.
- `scripts/verify-java.sh practice` → `verify-java: OK (practice)`. 76 tests, 0 failures. New tests:

  | Test class | Tests | What it covers |
  |---|---|---|
  | `ReviewRulesTest` | 5 | queue rule and quality boundaries |
  | `SentenceSm2Test` | 3 | §4.6 example: n=3, I=10, EF=2.5 → Again 1/1.96, Hard 25/2.36, Good 25/2.50, Easy 25/2.60 |
  | `SentenceReviewServiceTest` | 11 | queued-now, pass ignored, grade Good/Hard, failed review → +1 day without reset, retry unchanged, foreign reviewId, queue enrichment/locked/orphan purge/one call per lesson, limit validation, owner-only delete |
  | `LessonAccessGuardTest` | 5 | free / Pro+free → 403 text / Pro+premium / admin bypass without lookup / lookup failure → 503 |
  | `PremiumClientTest` | 3 | key `premium:{id}`, 5 min, `X-Internal-Token`, failure not cached |
  | `ContentServiceClientTest` | 2 | `isPro` parsing, missing → free, 404 → empty |

  `ShadowingServiceTest` was updated for the new constructor and typed content client.

### Frontend (in `frontend/`)
`node_modules` was installed with `docker run … node:20-alpine npm ci`, because the host has no npm.
- `node_modules/.bin/tsc --noEmit` → no output (OK).
- `node_modules/.bin/next lint` → `✔ No ESLint warnings or errors`.
- `TMPDIR=<scratch> node_modules/.bin/vitest run` → `Test Files 21 passed, Tests 191 passed`. New tests:
  `lib/pro-lesson.test.ts`, `lib/sentence-review.test.ts`, and a P2-A block in
  `components/layout/navItems.test.ts`. The sandbox denies vite-node temp files under `/tmp`, so
  `TMPDIR` must point elsewhere. A plain run passes the same tests but reports an `EPERM` error.

### Runtime (throwaway full stack, then removed)
- Setup: a copy of `docker-compose.yml` with the `container_name`s removed, gateway published on
  `127.0.0.1:18000`, and no frontend. It ran as project `p2a-test` with fresh random secrets and a
  fresh volume. The live `parroto-*` stack was not touched (all 9 containers still up).
- **Flyway** applied `content_db: 1 << Flyway Baseline >>, 2 lesson is pro` and
  `practice_db: 1 << Flyway Baseline >>, 2 sentence review` (success = t). Hibernate validated.
- Separately, both V2 scripts applied cleanly to a `pg_dump` copy of the **live** `content_db`
  (8 lessons, all `is_pro=false`) and `practice_db` (39 attempts) in a scratch postgres container.
- **Pro gate** (curl through the gateway):

  | Request | Result |
  |---|---|
  | Admin `PUT /content/lessons/{L1} {"isPro":true}` | `isPro: true` |
  | Free user, same PUT | 403 |
  | `?isPro=true` | returns only L1 |
  | Free user `GET` of the lesson detail | 200, `isPro: true`, 5 sentences |
  | Free user dictation attempt | **403** with the Vietnamese message |
  | Free user `request-upload-url` | **403** with the Vietnamese message |
  | Premium user (activation code) attempt | 201 |
  | Premium user `request-upload-url` | 200 |
  | Admin attempt (not premium) | 201 |
  | Free user with `premium:{id}=false` cached → `PROMO-DEMO3-MONTH` | key evicted (EXISTS 0), and `request-upload-url` returns **200 immediately** |

- **Review**:
  - A wrong dictation (accuracy 0) returned `review.action=queued, nextReviewAt=now`.
    `stats = {dueCount:1,total:1}`. The queue item had the sentence text and lesson title,
    `locked=false`. For the premium user, the Pro item had `lessonIsPro=true, locked=false`.
  - The DB row before grading was `0|0|2.50|02:51:04`. A correct attempt with `reviewId` returned
    `graded, quality 4, repetitions 1, interval 1`. The DB row after grading was
    `1|1|2.50|2026-10-16 02:51:04|100.00|<now>`, so **`next_review_at` moved by +1 day**.
  - A retry with the same `reviewId` returned `unchanged`.
  - Stats then showed `{dueCount:0,total:1,nextDueAt:2026-10-16…}`.
  - Another user's `DELETE` returned 404. The owner's `DELETE` returned 200. `limit=500` returned 400,
    and no token returned 401.
- Cleanup: `docker-compose -p p2a-test … down -v`, plus removal of the `p2a-test-*` images, the scratch
  postgres container and the temporary compose/env files. No `p2a` containers or volumes remain.

## Open risks / notes for merge
- **Conflicts with the gamification agent** (XP events in `DictationService` / `ShadowingService`):
  - Both services got 2 extra constructor parameters (`LessonAccessGuard`, `SentenceReviewService`).
  - The one-line reference-text fetch became `loadReferenceText(...)`.
  - There is one `.review(review)` builder line plus two private methods at the end of each service.
  - `Dictation/ShadowingAttemptResponse` and the requests got one new trailing record component.
    Anyone who constructs these records positionally must add an argument.
  - Expect trivial textual conflicts in the constructors.
- The frontend `DictationPractice` / `ShadowingPractice` got a `review` prop and 403 handling. Another
  agent showing XP there would touch the same files.
- `ContentServiceClient.fetchSentenceText` / `assertLessonExists` were replaced by `fetchSentence` /
  `fetchLesson` / `findLesson`. A branch still calling the old methods will fail to compile.
- The content-service lesson cache (24 h) keeps entries without `isPro` until it expires or is
  evicted. They deserialize as `false`, which is correct for every lesson until an admin sets the
  flag, and setting it evicts the entry.
- Shadowing's review update runs after the attempt has been saved, in its own transaction. Shadowing
  is intentionally not transactional because of the slow scoring call. A DB failure there returns 500
  but keeps the attempt.
- The admin learner-area redirect (`roleRedirect`) means admins normally never practise in the FE. The
  admin bypass is server-side only.
- `next build` / the frontend Docker image were not built (tsc + lint + vitest only).
