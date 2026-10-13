-- =============================================================================
-- T13: personal vocabulary decks (users add their own words to the notebook).
-- Apply to an EXISTING vocabulary_db (fresh volumes get this from init-db.sql).
-- Idempotent: safe to run more than once.
--
--   docker exec -i parroto-postgres psql -U parroto -d vocabulary_db -v ON_ERROR_STOP=1 \
--     < scripts/migrations/001_personal_vocab.sql
-- (snap's docker-compose exec -T does not forward stdin: it silently runs nothing)
-- =============================================================================

-- NULL = public (admin) deck; user ID = that user's hidden personal deck.
ALTER TABLE vocab_deck ADD COLUMN IF NOT EXISTS owner_id UUID;

-- At most one personal deck per user.
CREATE UNIQUE INDEX IF NOT EXISTS ux_vocab_deck_owner ON vocab_deck(owner_id) WHERE owner_id IS NOT NULL;
