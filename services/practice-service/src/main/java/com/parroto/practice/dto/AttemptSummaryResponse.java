package com.parroto.practice.dto;

import com.parroto.practice.entity.PracticeAttempt;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record AttemptSummaryResponse(
        UUID attemptId,
        UUID lessonId,
        UUID sentenceId,
        String mode,
        BigDecimal score,
        BigDecimal accuracy,
        OffsetDateTime createdAt
) {
    public static AttemptSummaryResponse from(PracticeAttempt a) {
        return AttemptSummaryResponse.builder()
                .attemptId(a.getId())
                .lessonId(a.getLessonId())
                .sentenceId(a.getSentenceId())
                .mode(a.getMode())
                .score(a.getScore())
                .accuracy(a.getAccuracy())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
