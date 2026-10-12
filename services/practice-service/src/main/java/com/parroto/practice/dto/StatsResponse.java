package com.parroto.practice.dto;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

@Builder
public record StatsResponse(
        long totalAttempts,
        double totalHoursLearned,
        double dictationAccuracy,
        double shadowingAccuracy,
        int currentStreak,
        int longestStreak,
        OffsetDateTime lastPracticeDate,
        long lessonsCompleted,
        double averageScore,
        AccuracyTrend accuracy
) {
    public record AccuracyTrend(List<Double> weekly, List<String> dates) {
    }
}
