-- P2-anki: Anki-style scheduling (learning steps, review intervals, lapses) for user_vocab.
-- Forward-only. V1 = baseline, V2 = gamification, V3 = groups/personal decks, V4 = this file.
-- Algorithm and constants: docs/architecture.md §4.6, service/srs/AnkiDefaults.java.

ALTER TABLE user_vocab
    ADD COLUMN state  character varying(16),
    ADD COLUMN step   integer NOT NULL DEFAULT 0,
    ADD COLUMN lapses integer NOT NULL DEFAULT 0;

-- 1) State of the existing rows (old plain SM-2: repetitions = streak of successful answers,
--    reset to 0 by "Again"; interval_days >= 1; next_review_at = last answer + interval):
--    * repetitions > 0                      -> REVIEW     (keeps interval and ease)
--    * never answered (last_review_at NULL)  -> NEW        (notebook words / "add to notebook")
--    * answered, repetitions = 0 (last "Again") -> RELEARNING step 0, due at its old next_review_at
UPDATE user_vocab SET state = CASE
    WHEN COALESCE(repetitions, 0) > 0 THEN 'REVIEW'
    WHEN last_review_at IS NULL THEN 'NEW'
    ELSE 'RELEARNING'
END;

-- 2) REVIEW: interval clamped to [1, 36500], ease >= 1.30, and the due timestamp moved to 04:00
--    (Asia/Ho_Chi_Minh) of the Anki day it fell on, so "due today" is a plain timestamp compare.
UPDATE user_vocab SET
    interval_days  = LEAST(GREATEST(COALESCE(interval_days, 1), 1), 36500),
    ease_factor    = GREATEST(COALESCE(ease_factor, 2.5), 1.30),
    next_review_at = ((((COALESCE(next_review_at, now()) AT TIME ZONE 'Asia/Ho_Chi_Minh') - interval '4 hours')::date
                        + time '04:00') AT TIME ZONE 'Asia/Ho_Chi_Minh')
WHERE state = 'REVIEW';

-- 3) RELEARNING: step 0 of the relearning steps; after it the card returns to REVIEW with the
--    minimum lapse interval (1 day). Lapse count is unknown before V4 and starts at 0.
UPDATE user_vocab SET
    interval_days  = 1,
    ease_factor    = GREATEST(COALESCE(ease_factor, 2.5), 1.30),
    next_review_at = COALESCE(next_review_at, now())
WHERE state = 'RELEARNING';

-- 4) NEW: no interval yet, starting ease.
UPDATE user_vocab SET
    interval_days  = 0,
    ease_factor    = 2.50,
    next_review_at = COALESCE(next_review_at, created_at, now())
WHERE state = 'NEW';

ALTER TABLE user_vocab ALTER COLUMN state SET DEFAULT 'NEW';
ALTER TABLE user_vocab ALTER COLUMN state SET NOT NULL;
ALTER TABLE user_vocab ALTER COLUMN next_review_at SET NOT NULL;
ALTER TABLE user_vocab ALTER COLUMN interval_days SET DEFAULT 0;
ALTER TABLE user_vocab ALTER COLUMN ease_factor SET DEFAULT 2.50;
ALTER TABLE user_vocab ADD CONSTRAINT ck_user_vocab_state
    CHECK (state IN ('NEW', 'LEARNING', 'REVIEW', 'RELEARNING'));
ALTER TABLE user_vocab ADD CONSTRAINT ck_user_vocab_step CHECK (step >= 0 AND lapses >= 0);

-- Study sessions read "due learning", "due review" and "new" per user (and per deck via card_id).
CREATE INDEX idx_user_vocab_user_state_due ON user_vocab (user_id, state, next_review_at);
