# T14-VOC – Admin deck edit (vocabulary-service)

Branch `feat/T14-voc-deck-update` (based on `40abe0c`).

## What changed
- **New endpoint** `PUT /vocabs/decks/{id}` (`DeckController.update`) → 200 `DeckResponse` (shape unchanged:
  `id, name, description, level, isProOnly, cardCount, createdAt`).
  Body `UpdateDeckRequest { name, description, level, isProOnly }` — **full replacement**: `name` is trimmed;
  blank `description` → `null`; `level` `null`/blank → `null`, otherwise upper-cased and must be A1–C2;
  `isProOnly` `null` → `false`. The FE should send the whole form.
- **Admin only** via `AdminGuard`. The guard's 403 message is now `"Bạn không có quyền quản trị"` (T14 common convention);
  this also applies to the existing vocabulary admin endpoints (POST/DELETE deck, POST/PUT/DELETE cards). Nothing in
  the repo matched on the old English text.
- **Personal decks** (`owner_id ≠ null`) and missing decks → 404 `"Không tìm thấy deck"`.
- **Validation** done in `DeckService.updateDeck` → **400** (Bean Validation would answer 422 in this service, same
  approach as T13-BE): `Tên deck không được để trống`, `Tên deck tối đa 255 ký tự`,
  `Trình độ phải là một trong A1, A2, B1, B2, C1, C2`. Missing/unparseable JSON body → existing 400 `Tham số không hợp lệ`.
  Validation runs before the DB lookup.
- **Cache**: after `saveAndFlush`, `invalidateByPattern("vocab:decks:*")` (SCAN-based). Card-page caches
  (`vocab:deck:{id}:cards:*`) hold no deck metadata and the Pro check is never cached, so they are left alone.

## Gateway
`/vocabs/decks` is mounted with `requireAuthForWrites`: only GET/HEAD/OPTIONS skip `verifyToken`, so PUT requires and
forwards the Bearer token (http-proxy-middleware passes headers and the unparsed body through; no body-parser on the
gateway). CORS (`cors` package default methods) and vocabulary-service `SecurityConfig` (PUT allowed, `anyRequest().authenticated()`)
already permit PUT. **Gateway not modified.**

## Tests
`DeckServiceUpdateDeckTest` (5): full update + level normalisation + cache invalidation; null level / blank description
cleared; validation messages & 400 (null/blank/256-char name, bad level, null body; 255 chars accepted);
personal/missing deck → 404 with no save and no cache invalidation; `AdminGuard` 403 message (case-insensitive admin email).

## Verification
`docker run --rm … maven:3.9-eclipse-temurin-21 mvn -B -q -Dmaven.repo.local=/tmp/.m2/repository clean test` in
`services/vocabulary-service` → exit 0, 50 tests, 0 failures/errors.
