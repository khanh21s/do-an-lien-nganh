package com.parroto.practice.dto;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record DictationAttemptResponse(
        UUID attemptId,
        UUID lessonId,
        UUID sentenceId,
        double accuracy,
        double score,
        int totalWords,
        int correctWords,
        List<WordScoreDto> wordScores,
        OffsetDateTime createdAt
) {
}
