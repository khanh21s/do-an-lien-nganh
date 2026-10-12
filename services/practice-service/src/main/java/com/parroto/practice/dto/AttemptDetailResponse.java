package com.parroto.practice.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.parroto.practice.entity.PracticeAttempt;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record AttemptDetailResponse(
        UUID attemptId,
        UUID lessonId,
        UUID sentenceId,
        String mode,
        BigDecimal score,
        BigDecimal accuracy,
        JsonNode wordScores,
        String audioRecordingUrl,
        BigDecimal duration,
        String difficulty,
        OffsetDateTime createdAt
) {
    public static AttemptDetailResponse from(PracticeAttempt a) {
        return AttemptDetailResponse.builder()
                .attemptId(a.getId())
                .lessonId(a.getLessonId())
                .sentenceId(a.getSentenceId())
                .mode(a.getMode())
                .score(a.getScore())
                .accuracy(a.getAccuracy())
                .wordScores(a.getWordScores())
                .audioRecordingUrl(a.getAudioRecordingUrl())
                .duration(a.getDuration())
                .difficulty(a.getDifficulty())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
