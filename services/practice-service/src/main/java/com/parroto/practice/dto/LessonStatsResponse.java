package com.parroto.practice.dto;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record LessonStatsResponse(
        long attempts,
        double avgAccuracy,
        double bestScore,
        OffsetDateTime lastAttempt
) {
}
