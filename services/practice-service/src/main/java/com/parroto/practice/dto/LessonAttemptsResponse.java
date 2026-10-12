package com.parroto.practice.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record LessonAttemptsResponse(
        List<AttemptSummaryResponse> attempts,
        Summary summary
) {
    public record Summary(double avgAccuracy, int totalAttempts) {
    }
}
