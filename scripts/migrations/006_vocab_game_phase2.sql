-- =============================================================================
-- T16: "Dịch nhanh" phase 2 + 3 — Survival / Daily challenge modes, leaderboards,
-- XP & levels, daily points goal, badges.
-- Apply to an EXISTING vocabulary_db after 004_vocab_game.sql (fresh volumes get this
-- from init-db.sql). Idempotent: safe to run more than once.
--
--   docker exec -i parroto-postgres psql -U parroto -d vocabulary_db -v ON_ERROR_STOP=1 \
--     < scripts/migrations/006_vocab_game_phase2.sql
-- =============================================================================

ALTER TABLE game_session ADD COLUMN IF NOT EXISTS lives INTEGER;                       -- SURVIVAL only
ALTER TABLE game_session ADD COLUMN IF NOT EXISTS fast_count INTEGER NOT NULL DEFAULT 0; -- clean answers < 3 s
ALTER TABLE game_session ADD COLUMN IF NOT EXISTS display_name VARCHAR(50);             -- masked email for leaderboards
ALTER TABLE game_session ADD COLUMN IF NOT EXISTS play_date DATE;                       -- start day, Vietnam time
ALTER TABLE game_session ADD COLUMN IF NOT EXISTS ranked BOOLEAN NOT NULL DEFAULT false; -- counts for leaderboards

UPDATE game_session SET play_date = (started_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date WHERE play_date IS NULL;
UPDATE game_session SET ranked = true
 WHERE mode = 'SPRINT' AND (source = 'LEVEL' OR (source = 'DECK' AND source_label <> 'Từ tự thêm')) AND NOT ranked;
ALTER TABLE game_session ALTER COLUMN play_date SET NOT NULL;

-- one Daily challenge attempt per user and day
CREATE UNIQUE INDEX IF NOT EXISTS ux_game_session_daily ON game_session(user_id, source_key) WHERE mode = 'DAILY';
CREATE INDEX IF NOT EXISTS idx_game_session_board ON game_session(mode, play_date, score DESC)
  WHERE status = 'FINISHED' AND ranked;

-- the 10 cards of each day's challenge (same for everyone), created on first play of the day
CREATE TABLE IF NOT EXISTS daily_challenge (
  day DATE PRIMARY KEY,
  card_ids TEXT NOT NULL,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- per-user game progress: XP (sum of finished round scores), daily points goal
CREATE TABLE IF NOT EXISTS game_profile (
  user_id UUID PRIMARY KEY,
  xp BIGINT NOT NULL DEFAULT 0,
  daily_goal INTEGER NOT NULL DEFAULT 200,
  goal_recorded_day DATE,          -- last day reported to practice-service (streak)
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO game_profile (user_id, xp)
SELECT user_id, SUM(score) FROM game_session WHERE status = 'FINISHED' GROUP BY user_id
ON CONFLICT (user_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS game_badge (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  code VARCHAR(40) NOT NULL,
  session_id UUID,                 -- round that earned it
  earned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (user_id, code)
);

ALTER TABLE daily_challenge OWNER TO vocabulary_user;
ALTER TABLE game_profile OWNER TO vocabulary_user;
ALTER TABLE game_badge OWNER TO vocabulary_user;
