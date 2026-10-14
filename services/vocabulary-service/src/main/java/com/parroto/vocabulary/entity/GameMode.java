package com.parroto.vocabulary.entity;

import java.time.Duration;

/**
 * Game modes of "Dịch nhanh" (T15/T16).
 * <ul>
 *   <li>SPRINT: as many words as possible in 60 s; missed words come back once.</li>
 *   <li>SURVIVAL: 3 lives, 15 s per word, words get harder (A → B1 → B2 → C); Premium.</li>
 *   <li>DAILY: the same 10 words for everyone, 20 s per word, one attempt per day.</li>
 * </ul>
 * SURVIVAL and DAILY have no round timer; {@link #roundDuration} is only a safety cap after
 * which an open round can no longer be answered.
 */
public enum GameMode {
    SPRINT(Duration.ofSeconds(60), null, null, null, true),
    SURVIVAL(Duration.ofMinutes(30), Duration.ofSeconds(15), 3, null, false),
    DAILY(Duration.ofMinutes(30), Duration.ofSeconds(20), null, 10, false);

    private final Duration roundDuration;
    private final Duration questionLimit;
    private final Integer lives;
    private final Integer questionCount;
    private final boolean retryMissed;

    GameMode(Duration roundDuration, Duration questionLimit, Integer lives, Integer questionCount, boolean retryMissed) {
        this.roundDuration = roundDuration;
        this.questionLimit = questionLimit;
        this.lives = lives;
        this.questionCount = questionCount;
        this.retryMissed = retryMissed;
    }

    public Duration roundDuration() {
        return roundDuration;
    }

    /** Time allowed per word, null when only the round timer counts (SPRINT). */
    public Duration questionLimit() {
        return questionLimit;
    }

    public Integer lives() {
        return lives;
    }

    public Integer questionCount() {
        return questionCount;
    }

    public boolean retryMissed() {
        return retryMissed;
    }

    /** The round timer is shown and ends the round (SPRINT). */
    public boolean timed() {
        return questionLimit == null;
    }
}
