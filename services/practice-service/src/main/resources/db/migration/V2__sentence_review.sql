-- P2-A2: per-user SRS queue of sentences to practise again ("Ôn câu sai").
-- A sentence enters the queue (per mode) when a Dictation attempt has accuracy < 100 or a
-- Shadowing attempt scores < 70; attempts made from the review screen grade it with SM-2
-- (docs/architecture.md §4.6). lesson_id is denormalised to fetch content per lesson.
CREATE TABLE sentence_review (
    id              uuid         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id         uuid         NOT NULL,
    sentence_id     uuid         NOT NULL,
    lesson_id       uuid         NOT NULL,
    mode            varchar(20)  NOT NULL CHECK (mode IN ('dictation', 'shadowing')),
    repetitions     integer      NOT NULL DEFAULT 0,
    interval_days   integer      NOT NULL DEFAULT 0,
    ease_factor     numeric(4,2) NOT NULL DEFAULT 2.50,
    next_review_at  timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_score      numeric(5,2),
    last_reviewed_at timestamp with time zone,
    created_at      timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_sentence_review_user_sentence_mode UNIQUE (user_id, sentence_id, mode)
);

-- Due queue / due count per user.
CREATE INDEX idx_sentence_review_user_due ON sentence_review (user_id, next_review_at);
