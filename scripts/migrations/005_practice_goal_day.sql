-- =============================================================================
-- T16: days on which the user reached a daily goal outside dictation/shadowing
-- (the "Dịch nhanh" game daily points goal). They count for the practice streak.
-- Apply to an EXISTING practice_db (fresh volumes get this from init-db.sql).
-- Idempotent: safe to run more than once.
--
--   docker exec -i parroto-postgres psql -U parroto -d practice_db -v ON_ERROR_STOP=1 \
--     < scripts/migrations/005_practice_goal_day.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS goal_day (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  day DATE NOT NULL,             -- calendar day in Vietnam time
  source VARCHAR(20) NOT NULL,   -- game
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (user_id, day, source)
);

ALTER TABLE goal_day OWNER TO practice_user;
