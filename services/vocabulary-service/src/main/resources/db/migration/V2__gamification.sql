-- P2-B: app-wide gamification (XP ledger, diamonds wallet, daily check-in, daily missions).
-- Reserved version: vocabulary-service V2 (docs/reports/P2-foundation.md). Forward-only.
--
-- xp_event       one row per XP gain. UNIQUE (source, ref_id) makes every award idempotent:
--                the ref_id encodes the anti-farming key (e.g. user:sentence:day), so a second
--                insert with the same key is a no-op (INSERT ... ON CONFLICT DO NOTHING).
-- diamond_event  same ledger shape for "kim cương". Amounts may become negative once a shop
--                exists (spending); the balance is SUM(amount).
-- gamification_profile  per-user state that is not a sum: check-in streak, masked display name
--                (leaderboard), and a row to lock when changing the wallet.

CREATE TABLE xp_event (
    id          bigserial PRIMARY KEY,
    user_id     uuid         NOT NULL,
    source      varchar(32)  NOT NULL,
    ref_id      varchar(200) NOT NULL,
    amount      integer      NOT NULL CHECK (amount >= 0),
    -- calendar day of the gain, Vietnam time (Asia/Ho_Chi_Minh)
    day         date         NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_xp_event_source_ref UNIQUE (source, ref_id)
);
CREATE INDEX idx_xp_event_user_day ON xp_event (user_id, day);
CREATE INDEX idx_xp_event_day ON xp_event (day);

CREATE TABLE diamond_event (
    id          bigserial PRIMARY KEY,
    user_id     uuid         NOT NULL,
    source      varchar(32)  NOT NULL,
    ref_id      varchar(200) NOT NULL,
    amount      integer      NOT NULL,
    day         date         NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_diamond_event_source_ref UNIQUE (source, ref_id)
);
CREATE INDEX idx_diamond_event_user_day ON diamond_event (user_id, day);

CREATE TABLE gamification_profile (
    user_id          uuid PRIMARY KEY,
    display_name     varchar(64),
    checkin_streak   integer     NOT NULL DEFAULT 0,
    last_checkin_day date,
    created_at       timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------- backfill of the game XP
-- Every finished "Dịch nhanh" round becomes a GAME row (ref = session id, the same key new
-- rounds use), dated by its play_date, so weekly numbers of past rounds are right too.
INSERT INTO xp_event (user_id, source, ref_id, amount, day, created_at)
SELECT s.user_id, 'GAME', CAST(s.id AS varchar), GREATEST(s.score, 0), s.play_date,
       COALESCE(s.finished_at, s.started_at)
FROM game_session s
WHERE s.status = 'FINISHED'
ON CONFLICT (source, ref_id) DO NOTHING;

-- game_profile.xp is the authoritative old total. If it is above the sum of the rounds (it never
-- was on the live data checked on 2026-10-15, but stay exact), the difference is kept as one
-- GAME_LEGACY row dated on the user's first round (or today when there is none), so that
-- SUM(xp_event.amount) == game_profile.xp for every existing player.
INSERT INTO xp_event (user_id, source, ref_id, amount, day)
SELECT p.user_id, 'GAME_LEGACY', CAST(p.user_id AS varchar), p.xp - COALESCE(r.total, 0),
       COALESCE(r.first_day, CAST(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh' AS date))
FROM game_profile p
LEFT JOIN (SELECT user_id, SUM(GREATEST(score, 0)) AS total, MIN(play_date) AS first_day
           FROM game_session WHERE status = 'FINISHED' GROUP BY user_id) r ON r.user_id = p.user_id
WHERE p.xp > COALESCE(r.total, 0)
ON CONFLICT (source, ref_id) DO NOTHING;

-- Leaderboard names of existing players: the masked name of their latest round.
INSERT INTO gamification_profile (user_id, display_name)
SELECT DISTINCT ON (user_id) user_id, display_name
FROM game_session
WHERE display_name IS NOT NULL
ORDER BY user_id, started_at DESC
ON CONFLICT (user_id) DO NOTHING;
