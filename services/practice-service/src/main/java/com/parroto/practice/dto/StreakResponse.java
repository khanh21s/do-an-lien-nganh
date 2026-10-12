package com.parroto.practice.dto;

import java.time.OffsetDateTime;

public record StreakResponse(int currentStreak, OffsetDateTime lastPracticeDate) {
}
