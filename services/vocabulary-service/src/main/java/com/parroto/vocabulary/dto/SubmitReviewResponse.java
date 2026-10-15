package com.parroto.vocabulary.dto;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Result of one answer: the card's new schedule plus the refreshed session (counters, next card).
 * {@code interval}, {@code easeFactor}, {@code repetitions}, {@code nextReviewAt}, {@code message}
 * keep their Phase-1 names; {@code interval} = {@code intervalDays} (0 while in learning).
 */
@Builder
public record SubmitReviewResponse(
        UUID userVocabId,
        UUID cardId,
        int rating,
        String state,
        int step,
        int intervalDays,
        int interval,
        double easeFactor,
        int repetitions,
        int lapses,
        OffsetDateTime nextReviewAt,
        /** Same text the button showed, e.g. "10 phút" or "4 ngày". */
        String label,
        String message,
        StudyDtos.StudySession session
) {
}
