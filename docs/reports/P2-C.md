# P2-C: vocabulary expansion (personal decks, groups, import, dictionary)

Branch `feat/phase2-c-vocab`, based on `feat/phase2` at `295cabd`. It covers spec §5 group 4: personal decks and groups, file import, the dictionary and "click a word → save it". Migration **V3** of vocabulary-service is mine; V2 (gamification) belongs to agent B.

## 1. Data model (`V3__vocab_groups_personal_decks.sql`)

| Change | Why |
|---|---|
| `vocab_deck.is_notebook boolean NOT NULL DEFAULT false`, then `UPDATE … SET true WHERE owner_id IS NOT NULL` | Until now a user owned exactly one personal deck: the notebook "Sổ tay cá nhân". That deck is now marked explicitly, so the same user can own more decks. |
| Dropped `ux_vocab_deck_owner`. New `ux_vocab_deck_notebook (owner_id) WHERE is_notebook` and `idx_vocab_deck_owner`. CHECK: a notebook must have an owner. | Each user still has at most one notebook. The lazy-create `INSERT … ON CONFLICT (owner_id) WHERE is_notebook` relies on this index. |
| New `vocab_group(id, deck_id → vocab_deck ON DELETE CASCADE, name varchar(100), sort_order, created_at, updated_at)`. Unique `(deck_id, lower(name))`. Updated-at trigger. | Named, ordered groups inside any deck, public or personal. |
| `vocab_card.group_id uuid → vocab_group ON DELETE SET NULL`. Index on `group_id`. Index on `(deck_id, lower(btrim(word)))`. | Deleting a group keeps its cards; they become ungrouped. The second index serves duplicate checks. |

Entities: `VocabDeck.isNotebook` (builder default `false`), `VocabCard.groupId` (a plain UUID column, not a relation), and a new `VocabGroup`.

## 2. Rules and limits

- **The notebook keeps its T13 behaviour.** Every card in it has a `user_vocab` row that is due immediately. Its cap stays at 2,000. It cannot be renamed or deleted.
- **Other personal decks behave like public decks for studying.** A card enters SRS the first time it is graded (existing `add-from-lesson` flow). Removing such a word from the notebook (`DELETE /vocabs/user/{id}`) now deletes only the `user_vocab` row; the card stays in its deck.
- **Limits live in one place:** `service/deck/VocabLimits`.

  | Plan | Personal decks (notebook not counted) | Cards in those decks (total) |
  |---|---|---|
  | Free | 5 | 500 |
  | Premium | 50 | 8,000 |

  `PremiumClient.isPremium` decides the plan. Other limits: at most 100 groups per deck; import files up to 2 MB and 2,000 rows. Public decks have no deck or card limit.
- **Ownership** (`DeckAccess`):
  - Personal deck: only the owner can see or manage it. Anyone else, admins included, gets 404.
  - Public deck: anyone can read it; managing it requires admin (403 otherwise).
- **Concurrency:** writes lock the deck row (`findByIdForUpdate`). Checks of the per-user card limit also lock the user's notebook row, which serialises them per user.

## 3. Endpoints (gateway paths)

| Method and path | Who | Notes |
|---|---|---|
| `GET /vocabs/user/decks` | user | `{decks: PersonalDeck[], usage: DeckUsage}`. Notebook first; it is created if missing. |
| `POST /vocabs/user/decks`, `PUT/DELETE /vocabs/user/decks/{id}` | owner | Body `{name, description?}`. Limit exceeded → 400 (Vietnamese message). Notebook → 400. |
| `POST /vocabs/user/decks/{id}/cards` | owner | Body `{word, meaning, ipa?, partOfSpeech?, example?, exampleVi?, groupId?}`. Duplicate → 409. A notebook card is created through `PersonalWordService`. |
| `PUT/DELETE /vocabs/user/cards/{cardId}` | owner | On PUT, `groupId` is set as sent (null means ungrouped). |
| `GET /vocabs/decks/{id}` | public / owner | `DeckDetail` with `groups[]` (each with `cardCount`), `ungroupedCount`, `personal`, `isNotebook`, `canManage`. |
| `GET /vocabs/decks/{id}/groups`; `POST …/groups`; `PUT …/groups/{gid}`; `PUT …/groups/order {groupIds}`; `DELETE …/groups/{gid}` | read: public/owner; write: owner or admin | Duplicate name → 409. `order` must list every group of the deck exactly once. |
| `PUT /vocabs/decks/{id}/cards/group {cardIds, groupId\|null}` | owner or admin | Moves cards; ids belonging to other decks are ignored. |
| `GET /vocabs/decks/{id}/cards?groupId=<uuid\|none>` | as before | Existing `?seed=` shuffle and `searchWord` still work. `none` = ungrouped cards. `CardResponse` gains `groupId`. |
| `POST /vocabs/decks/{id}/cards?groupId=` | admin | Optional group for cards created by admins. |
| `POST /vocabs/decks/import/preview` (multipart `file`, optional `?deckId=`) | user | Saves nothing. Returns rows with `errors[]`, `duplicateInFile`, `existsInDeck`, counts and `warnings`. |
| `POST /vocabs/decks/{id}/import {groupId? \| newGroupName?, rows[]}` | owner or admin | Validates the rows again and skips words already in the deck (notebook: also words already in the user's notebook). Over-limit → 400 before anything is saved. Returns `{received, imported, skippedDuplicate, skippedInvalid, errors[]}`. |
| `GET /vocabs/dictionary/{word}` | public (Bearer optional) | `{query, word, found, ipa, audioUrl, meaningsVi[], partsOfSpeech[], definitionsEn[{partOfSpeech, definition, example}], examples[{en, vi}], source, externalStatus}`. Has its own rate limit in the gateway (60/min per IP). |

## 4. Import formats

The parser is `service/importer/VocabFileParser`: pure, no Spring. Row validation is `ImportRowValidator`, with the same length limits as hand-added words.

- **CSV**
  - Strict UTF-8 (BOM is stripped); any other encoding → 400 "File phải được lưu với mã hoá UTF-8".
  - The delimiter (comma or semicolon) is detected from the first data line.
  - Quoting follows RFC 4180: quoted fields, doubled quotes, and line breaks inside quotes.
  - A header row is optional. It is matched by name, ignoring Vietnamese accents (`word/từ/term`, `meaning/nghĩa`, `ví dụ`, `nghĩa ví dụ`…). Without a header, columns are read in order: word, meaning, ipa, partOfSpeech, example, exampleVi.
  - A `.csv` file with no delimiter at all is read with the TXT rules, and the preview says so.
- **TXT**
  - One entry per line, separated by TAB, ` - ` (also – and —), `:` or `=`. The earliest separator wins. A separator inside `/ipa/` or `[ipa]` is ignored.
  - A tab-separated line may carry all six CSV columns.
  - Leading numbering or bullets are stripped. A trailing `/ipa/` or `(n)` after the word is pulled into its own field.
  - Blank lines, `#` and `//` comments, and header lines ("Word⇥Meaning") are skipped. A line without a separator becomes an error row.
- **PDF (best-effort; the UI says so)**
  - Text is extracted with PDFBox 3.0.3, up to 100 pages. A subclassed `PDFTextStripper` writes a TAB where the horizontal gap between words is wide, so table cells become columns. Runs of 3+ spaces also count as a gap.
  - Each line then goes through the TXT rules. Lines without a separator (titles, page numbers) and table headers are skipped, counted, and reported as a warning.
  - Scanned or encrypted PDFs → 400 with a clear message.
- **CSV template:** the "⬇ Tải file CSV mẫu" button in the wizard generates it client-side (`buildCsvTemplate`, UTF-8 BOM for Excel).

## 5. Dictionary

1. **Normalise the word** (`WordNormalizer`): lowercase, straight apostrophes, surrounding punctuation removed. Only letters, `'`, `-` and spaces are allowed, up to 64 characters; anything else → 400.
2. **Build candidates:** the word itself first, then lemma guesses: `'s`, `-s`, `-es`, `-ies→y`, `-ied→y`, `-ed` (stem, stem+e, undoubled stem), `-ing` (same three), and `lying→lie`. Multi-word phrases get no guesses.
3. **Look in the cards.** One query covers free public decks plus all of the caller's own decks; the caller's cards come first. Pro-only decks are excluded so nothing gated leaks. The first candidate with a card match becomes the response `word`.
4. **Call the Free Dictionary API** (`FreeDictionaryClient`):
   - It has its own RestTemplate with 1.5 s connect / 2.5 s read timeouts.
   - It is asked about that word, or about the candidates in order when no card matched, with at most 2 uncached calls per lookup.
   - The first failure ends the lookup, and the API is then skipped for 30 s (`externalStatus: "skipped"`).
5. **Redis cache:** key `vocab:dict:ext:v1:{word}`, stored as a JSON string. Hits are kept 7 days and misses (API 404) 1 day. Failures are not cached. If Redis is down, lookups continue without the cache.
6. **Merge:** IPA from a card first, otherwise from the API. Audio from a card, otherwise the API's US recording. Up to 5 Vietnamese meanings and 3 examples from cards, up to 10 English definitions. An API failure never causes a 500; the response carries whatever is known.

## 6. Frontend

- **Click a word.** Words in `LessonTranscript` and in the dictation answer (`AddableSentence`, and also `WordDiffView`) open `WordLookup`, which shows the `WordInfoModal` sheet: IPA, 🔊 (audio file, or speech synthesis as a fallback), Vietnamese meaning, English definitions and a source note.
- **Save it.** "+ Thêm vào sổ tay / bộ thẻ" opens `AddWordModal` pre-filled with the lemma, the first meaning, IPA, part of speech, and the clicked sentence as the example. With `chooseDeck`, a deck/group picker appears; the notebook is the default, so saving behaves exactly as before. Guests see "Đăng nhập để lưu từ" instead.
- **Learner pages:**
  - `/vocabulary/my-decks`: usage bar, create/rename/delete decks, and Học / Quản lý / import buttons.
  - `/vocabulary/my-decks/[id]`: group manager (add, rename, ↑↓ reorder, delete), a card list with search, group chips and a per-card group select, and a card editor.
  - `/vocabulary/my-decks/import`: the import wizard.
  - `/vocabulary/decks/[id]`: group chips for studying; the seeded shuffle is kept and the key changes per group. It also works for personal decks, using `GET /vocabs/decks/{id}` for the deck name.
- **Admin:**
  - `/admin/vocabulary/[deckId]`: groups panel, group filter and per-card group select.
  - `/admin/vocabulary/import`: same wizard, public decks only.
  - Import buttons on the admin list and deck pages.
- **Navigation:** new entry "Bộ thẻ của tôi", plus a link on `/vocabulary`.
- **Shared files:** additions in `lib/types.ts`, `lib/queryKeys.ts`, `lib/endpoints/vocabulary.ts` and `navItems.ts` sit in blocks marked `// --- P2-C ---`.

## 7. Decisions

1. **The notebook is outside the new limits.** It keeps its existing 2,000-word cap, and the 5/500 and 50/8,000 limits apply to the user's other personal decks. Counting the notebook in the 500-card free limit would have cut existing free users from 2,000 to 500 notebook words, which is a product change nobody asked for.
2. **Imports over the card limit are rejected whole**, with a 400 that says how many cards still fit. Nothing is imported partially. The wizard warns before sending and lets the user untick rows.
3. **Cards in custom decks are not scheduled automatically.** They enter SRS when first graded, like cards in public decks. Only the notebook auto-schedules, as before.
4. **Deleting a group keeps its cards** (they become ungrouped). Deleting a custom deck deletes its cards and their SRS progress; the confirm dialog says so.
5. **Endpoint placement.** Learner deck CRUD sits under `/vocabs/user/…`, which the gateway already protects. Groups, deck detail and import use `/vocabs/decks/{id}/…` and serve both learners and admins under the ownership rule. The only new gateway route is `/vocabs/dictionary`.
6. **The dictionary is public** so guests can use it on lesson pages. The gateway limits it to 60 requests/min per IP, and the response carries `Cache-Control: private, max-age=300`.
7. **Lemma in the save form.** When the dictionary resolves a lemma ("running" → "run"), the save form is pre-filled with the lemma. The user can edit it.

## 8. Verification (2026-10-15)

- **Java:** `scripts/verify-java.sh vocabulary` → `verify-java: OK (vocabulary)`. vocabulary-service ran **147 tests**, 0 failures (84 before this branch). New test classes:
  - `VocabFileParserTest`: CSV header/positional/semicolon/BOM/quotes/multiline, TXT separators, IPA-colon masking, bullets, header skip, the committed Vietnamese PDF fixture `src/test/resources/import/vocab-sample.pdf` (made with the `PdfFixtures.main` generator), an in-memory Helvetica table PDF, a broken PDF and unknown file types.
  - `ImportRowValidatorTest`, `VocabImportServiceTest`: duplicates, invalid rows, free/premium limits, notebook cap and scheduling, ownership, 2 MB and 2,000-row limits.
  - `WordNormalizerTest`, `FreeDictionaryClientTest` (MockRestServiceServer: parsing, path encoding, 404 → miss, 5xx, disabled), `DictionaryServiceTest` (merge, lemma, cache hit, cached miss, failure fallback and cool-down, call cap, Redis down, invalid word, guests).
  - `PersonalDeckServiceTest`, `DeckGroupServiceTest`: limits, ownership (404/403), notebook rules, group names, reorder, delete, moves.
- **Gateway:** `scripts/verify-gw.sh` → `verify-gw: OK`.
- **Frontend** (in `frontend/`):
  - `node_modules/.bin/tsc --noEmit` → OK.
  - `node_modules/.bin/vitest run` → 22 files, **199 tests** passed. New: `importHelpers.test.ts`, `wordInfo.test.ts`, `myDecks.test.ts`.
  - `node_modules/.bin/next lint` → no warnings or errors.
  - `next build` → compiled; all 30 pages were generated, including the new ones.
- **Runtime.** Everything below ran on throwaway containers `p2c-pg`, `p2c-redis`, `p2c-vocab` and `p2c-gw` on network `p2c-net`. `p2c-pg` was loaded with `pg_dump --no-owner` of the live `vocabulary_db` (1,008 cards, 11 notebooks). All checks went through the new gateway image.
  - **Migration:** Flyway logged `baselined schema with version: 1` → `Migrating schema "public" to version "3 - vocab groups personal decks"` → success. Hibernate validate passed. All 11 existing personal decks became notebooks. A restart logged `Schema "public" is up to date`.
  - **Personal deck and groups:** the deck was created (name normalised to "IELTS Travel"). Groups Unit 1 and Unit 2 were created; "unit 1" → 409. Another user, an admin, and a guest all got 404 on the personal deck.
  - **Import:**
    - CSV (semicolon, BOM, quotes): preview showed 5 rows (2 valid, 2 errors, 1 duplicate). The commit into Unit 1 returned `imported 2, skippedDuplicate 1, skippedInvalid 2`.
    - TXT into a new group "Unit 3": imported 5; the line without a separator was invalid.
    - PDF fixture: preview showed 5 rows with 3 skipped lines (title, table header, page number). The commit imported 3 and skipped 2 that were already in the deck.
    - Deck detail afterwards: Unit 1 = 2, Unit 3 = 5, ungrouped = 3.
  - **Limits:**
    - The 6th custom deck → 400 "Tài khoản miễn phí tạo tối đa 5 bộ thẻ…".
    - Importing 491 rows with 10 used → 400 "…còn trống 490 thẻ"; 490 rows → imported; one more card → 400 "còn trống 0".
    - With `premium:{userId}=true` in Redis the card was accepted, and usage showed 50 / 8,000.
    - A 2.2 MB file → 400 "File vượt quá 2 MB"; 2,001 rows → 400; `.docx` → 400.
    - A learner importing into a public deck → 403; without a token → 401.
    - An admin created a group on a public deck and imported into it (1 imported, 1 duplicate skipped). A guest saw the group and could study by it. A learner creating a group there → 403.
  - **Study:** `?groupId=<Unit 1>&seed=abc` returned the same order twice, with only that group. `groupId=none` → 494 ungrouped cards. Public `?seed=s1` and `?seed=s2` gave different orders.
  - **Dictionary:**
    - `Hello,` → `cards+dictionary`: "xin chào", 7 English definitions, audio, `ok`. Redis key `vocab:dict:ext:v1:hello` with TTL 604800 (7 days); the next lookup took 11 ms.
    - `serendipity` and `ubiquitous` → `dictionary` (external only, 0.1–0.2 s), cached; the repeat took 11 ms.
    - `airports` → lemma `airport` from cards. With a token the learner's own card came first (own example); without a token the public card was used.
    - While api.dictionaryapi.dev was slow (20+ s, Cloudflare 522) the response was still 200 with the card data and `externalStatus: "error"`, and the next lookup during the cool-down returned in 10 ms with `skipped`.
    - `1234` → 400.
    - 65 requests in one minute → 60 × 200 and 5 × 429.
    - **Not shown at runtime:** the 1-day miss TTL. The API returned 522s or timeouts instead of 404s for nonsense words while I tested, so this is covered by unit tests only.
  - **Cleanup:** I removed the containers with their volumes, the network and the `p2c-*` images, and ran `docker volume prune -f`. That command removes unused anonymous volumes daemon-wide, not only mine. The live `parroto-*` stack was never stopped and never written to; I only ran `pg_dump` and GET requests against it. Live `vocabulary_db` has no `vocab_group` and no `flyway_schema_history`.
- **Not run:** a browser or Playwright pass of the new pages (the e2e suite needs the integrated stack). The T13 e2e spec was updated for the new click flow but not executed.

## 9. Pre-existing bug fixed

`GET /vocabs/decks/{id}/cards?seed=` answered **500** on this base (commit 54b6614, not deployed live yet). Hibernate 6 rewrote `c.id::text` in the native query to `c.id:text`. All shuffled queries now use `CAST(c.id AS text)` (commit `c7fc8a3`). If feat/phase2 still has the old query, this commit fixes it there too.

## 10. Risks and merge notes

- **Merging with feat/phase2** (it already has agent B's V2 and `/internal` changes). Expected overlaps are small:
  - `SecurityConfig`: one added `permitAll` line for `/vocabs/dictionary/**`.
  - `api-gateway/server.js`: one route.
  - `rateLimiter.js`: one new export.
  - `PersonalWordService`: lock by deck id in `update`, and a non-notebook branch in `remove`.
  - `DeckService`: `groupId` overloads, plus optional setter injection of `VocabGroupRepository`.
  - `VocabDeckRepository`: the notebook queries now filter `is_notebook`.
  - Migration order is V2 (B) then V3 (mine). They are independent, apart from both being on vocabulary_db.
- **Live first deploy:** V3 updates `updated_at` on the existing notebook rows (the trigger fires on the `UPDATE`). This is harmless.
- **Free Dictionary API:** free, with no SLA, and slow or failing at times (seen during testing). The cool-down and caching keep the app responsive. English definitions are cached for 7 days.
- **Abuse:** dictionary results are cached per normalised word, so the key space grows with distinct lookups (7-day TTL). The rate limit is the main guard.
- **PDF parsing is heuristic.** Unusual layouts (multi-column pages, cells that wrap) may produce wrong rows. The preview is the safety net, and the UI says parsing is approximate.
- **Gaps:** the admin "Thêm thẻ" form does not pick a group (cards can be moved afterwards with the per-card select). Deck limits are checked when writing, not when the plan changes: a Premium user who downgrades keeps their extra decks but cannot add more.
