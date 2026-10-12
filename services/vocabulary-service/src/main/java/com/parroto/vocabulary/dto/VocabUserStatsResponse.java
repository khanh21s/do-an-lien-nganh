package com.parroto.vocabulary.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record VocabUserStatsResponse(
        long totalLearned,
        long dueToday,
        long dueThisWeek,
        BySrs bySRS,
        double accuracy
) {
    public record BySrs(@JsonProperty("new") long newCount, long learning, long review) {
    }
}
