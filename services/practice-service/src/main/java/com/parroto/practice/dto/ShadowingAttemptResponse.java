package com.parroto.practice.dto;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record ShadowingAttemptResponse(
        UUID attemptId,
        String recordingUrl,
        double score,
        double accuracy,
        double duration,
        List<ShadowingWordScoreDto> wordScores,
        OffsetDateTime createdAt,
        String scorer,
        /** P2-A2: effect on the sentence review queue, null when not queued. */
        SentenceReviewOutcome review
) {
}
