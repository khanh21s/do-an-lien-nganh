package com.parroto.practice.dto;

import com.parroto.practice.entity.SentenceReview;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * P2-A2: what an attempt did to the review queue (field {@code review} of the dictation/shadowing
 * attempt responses; null when the sentence is not in the queue and the attempt passed).
 *
 * @param action  "queued" (failed attempt: added or reset, due now), "graded" (review-screen attempt
 *                on a due item, SM-2 applied), "unchanged" (in the queue, schedule not touched)
 * @param quality SM-2 quality used when {@code action = graded}, otherwise null
 */
public record SentenceReviewOutcome(
        UUID reviewId,
        String action,
        Integer quality,
        int repetitions,
        int intervalDays,
        BigDecimal easeFactor,
        OffsetDateTime nextReviewAt
) {
    public static final String QUEUED = "queued";
    public static final String GRADED = "graded";
    public static final String UNCHANGED = "unchanged";

    public static SentenceReviewOutcome of(SentenceReview r, String action, Integer quality) {
        return new SentenceReviewOutcome(r.getId(), action, quality, r.getRepetitions(), r.getIntervalDays(),
                r.getEaseFactor(), r.getNextReviewAt());
    }
}
