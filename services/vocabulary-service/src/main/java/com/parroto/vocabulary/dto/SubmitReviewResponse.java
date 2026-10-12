package com.parroto.vocabulary.dto;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record SubmitReviewResponse(
        UUID userVocabId,
        OffsetDateTime nextReviewAt,
        double easeFactor,
        int interval,
        int repetitions,
        String message
) {
}
