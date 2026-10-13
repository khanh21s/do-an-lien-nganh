# T13-BE – Personal words in the notebook (vocabulary-service)

Branch `feat/T13-be-personal-words` (based on `61d015f`).

## What changed
- **Schema**: `vocab_deck.owner_id UUID` + partial unique index `ux_vocab_deck_owner (owner_id) WHERE owner_id IS NOT NULL`
  in `init-db.sql`; idempotent `scripts/migrations/001_personal_vocab.sql` for existing volumes (README → "Operations notes":
  `docker-compose exec -T postgres psql -U parroto -d vocabulary_db < scripts/migrations/001_personal_vocab.sql`, then restart
  vocabulary-service because `ddl-auto: validate` needs the column).
- **Entity** `VocabDeck.ownerId` (+ `isPersonal()`, `isOwnedBy(userId)`).
- **Visibility**: `GET /vocabs/decks` filters `owner_id IS NULL`; `GET /vocabs/decks/{id}/cards` of a personal deck → 404
  for guests and other users (owner gets 200); admin deck/card writes (`POST /decks/{id}/cards`, `DELETE /decks/{id}`,
  `PUT|DELETE /cards/{id}`) treat personal decks as missing (404); `add-from-lesson` with another user's personal card → 404.
- **New** `PersonalWordService` + `CreatePersonalWordRequest`:
  - `POST /vocabs/user/words` → 201 `LearnedWordResponse` (`custom=true`, due now → in review queue immediately).
  - `PUT /vocabs/user/words/{userVocabId}` → 200. Full replacement: every field of the body is written (omitted/blank
    optional fields become `null`), so the FE should send the whole form.
  - `DELETE /vocabs/user/{userVocabId}` moved here: custom word → also deletes the card and recounts `card_count`;
    public-deck word → only the `user_vocab` row (as before). 404 message is now `"Không tìm thấy từ"`.
  - Personal deck `"Sổ tay cá nhân"` is created lazily with `INSERT … ON CONFLICT DO NOTHING` and then row-locked
    (`SELECT … FOR UPDATE`), so concurrent adds by one user serialize the duplicate check, the 2000 limit and `card_count`.
  - Validation in `CreatePersonalWordRequest.normalized()` (trim, blank → null, limits) → **400** as the contract says
    (Bean Validation would answer 422 in this service). Messages: `Vui lòng nhập từ`, `Vui lòng nhập nghĩa của từ`,
    `Từ tối đa 100 ký tự`, `Nghĩa tối đa 500 ký tự`, `Phiên âm tối đa 100 ký tự`, `Từ loại tối đa 50 ký tự`,
    `Câu ví dụ tối đa 500 ký tự`, `Bản dịch câu ví dụ tối đa 500 ký tự`. Missing/invalid JSON body → existing 400 `Tham số không hợp lệ`.
  - Contract errors verbatim: 409 `Từ này đã có trong sổ tay`, 404 `Không tìm thấy từ`, 403 `Chỉ sửa được từ bạn tự thêm`,
    400 `Sổ tay cá nhân đã đạt giới hạn 2000 từ`.
- **LearnedWordResponse** gains `custom`, `ipa`, `partOfSpeech`, `example`, `exampleVi`. `findLearned` now `JOIN FETCH`es
  card and deck (with a separate count query) → no N+1.
- **Cache**: after commit, `vocab:user:{userId}:*` and `vocab:deck:{personalDeckId}:*` are invalidated (Redis errors only
  logged). Note: vocabulary-service currently has no per-user stats cache (stats are computed live), so the first pattern is
  forward-safe; the personal deck's card pages are never cached either (only guest reads are cached, and guests get 404).

## Verification
- `mvn -B -q clean test` (Docker `maven:3.9-eclipse-temurin-21`): 45 tests, 0 failures
  (new: `PersonalWordServiceTest` 14, `DeckServicePersonalDeckTest` 3, `LearnedWordResponseTest` +1).
- Real run: throwaway `postgres:16-alpine` (initdb `00-roles.sh` + `init-db.sql`) + `redis:7-alpine` on a private network,
  jar on `eclipse-temurin:21-jre-alpine` → started, `ddl-auto: validate` OK. Migration applied twice to an old-shape table
  and to the fresh DB: no errors. HS256 access JWTs minted like auth-service (`type=access`, `jti`).
  Results: POST 201 (`custom=true`, in `/learned` and `/review-queue`); dup `"SERENDIPITY  "` 409; word learned from a public
  deck 409; PUT 200; PUT renaming onto another word 409; PUT public word 403; PUT/DELETE by another user 404; validation 400
  (blank word, 101 chars, missing meaning, empty body); `GET /vocabs/decks` never lists the personal deck; personal deck cards:
  guest 404 / other user 404 / owner 200; DELETE custom → card removed, `card_count` 1→0; DELETE public word keeps the card;
  2000 cards → 400; no token → 401; 7 parallel POSTs for a fresh user (incl. 3× same word) → 1 deck, 5 cards, `card_count`=5, 2×409.
  All containers/network removed afterwards; `parroto-*` untouched.

## Deviations
None from the API contract. Extras: admin endpoints and `add-from-lesson` also hide personal decks (design says admin must not
touch them); DELETE's 404 message changed from `Vocabulary entry not found` to the contract's `Không tìm thấy từ`.
