-- =============================================================================
-- T15: "Dịch nhanh" game (Vietnamese meaning -> type the English word).
-- Apply to an EXISTING vocabulary_db (fresh volumes get this from init-db.sql).
-- Idempotent: safe to run more than once.
--
--   docker exec -i parroto-postgres psql -U parroto -d vocabulary_db -v ON_ERROR_STOP=1 \
--     < scripts/migrations/004_vocab_game.sql
-- (snap's docker-compose exec -T does not forward stdin: it silently runs nothing)
-- =============================================================================

-- One played round. Scoring is computed server-side; the queues hold the cards still to ask.
CREATE TABLE IF NOT EXISTS game_session (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  mode VARCHAR(20) NOT NULL,          -- SPRINT (60 s)
  source VARCHAR(20) NOT NULL,        -- DECK | NOTEBOOK | LEVEL
  source_key VARCHAR(80) NOT NULL,    -- deck:<id> | notebook | level:B2 (personal-best scope)
  source_label VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | FINISHED | ABANDONED
  card_queue TEXT NOT NULL DEFAULT '',  -- comma-separated card ids not asked yet
  retry_queue TEXT NOT NULL DEFAULT '', -- "<cardId>:<seq>" missed cards asked again at that seq
  score INTEGER NOT NULL DEFAULT 0,
  combo INTEGER NOT NULL DEFAULT 0,
  best_combo INTEGER NOT NULL DEFAULT 0,
  correct_count INTEGER NOT NULL DEFAULT 0,
  near_count INTEGER NOT NULL DEFAULT 0,
  wrong_count INTEGER NOT NULL DEFAULT 0,
  skipped_count INTEGER NOT NULL DEFAULT 0,
  previous_best INTEGER,              -- best score of the same scope before this round
  srs_updated INTEGER NOT NULL DEFAULT 0,
  started_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ NOT NULL,
  finished_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_game_session_user_status ON game_session(user_id, status);
CREATE INDEX IF NOT EXISTS idx_game_session_best ON game_session(user_id, source_key, score DESC);

-- One question of a round (asked_at set when shown, answered_at when answered/skipped).
CREATE TABLE IF NOT EXISTS game_answer (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES game_session(id) ON DELETE CASCADE,
  seq INTEGER NOT NULL,
  card_id UUID NOT NULL REFERENCES vocab_card(id) ON DELETE CASCADE,
  is_retry BOOLEAN NOT NULL DEFAULT false,
  asked_at TIMESTAMPTZ NOT NULL,
  answered_at TIMESTAMPTZ,
  answer VARCHAR(255),
  result VARCHAR(10),                 -- CORRECT | NEAR | WRONG | SKIPPED
  hints_used INTEGER NOT NULL DEFAULT 0,
  points INTEGER NOT NULL DEFAULT 0,
  UNIQUE (session_id, seq)
);

-- Run as the superuser: hand the new tables to the service account.
ALTER TABLE game_session OWNER TO vocabulary_user;
ALTER TABLE game_answer OWNER TO vocabulary_user;
