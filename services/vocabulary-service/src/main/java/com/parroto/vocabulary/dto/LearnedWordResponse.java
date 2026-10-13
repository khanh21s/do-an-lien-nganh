package com.parroto.vocabulary.dto;

import com.parroto.vocabulary.entity.UserVocab;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record LearnedWordResponse(
        UUID userVocabId,
        UUID cardId,
        String word,
        String meaning,
        int repetitions,
        double accuracy,
        /** SM-2 schedule (VOC-05): next due time, current interval in days, ease factor. */
        OffsetDateTime nextReviewAt,
        int intervalDays,
        double easeFactor
) {
    public static LearnedWordResponse from(UserVocab uv) {
        double computedAccuracy = uv.getQuality() != null ? (uv.getQuality() >= 3 ? 100.0 : uv.getQuality() * 20.0) : 0.0;
        return LearnedWordResponse.builder()
                .userVocabId(uv.getId())
                .cardId(uv.getCard().getId())
                .word(uv.getCard().getWord())
                .meaning(uv.getCard().getMeaning())
                .repetitions(uv.getRepetitions() != null ? uv.getRepetitions() : 0)
                .accuracy(computedAccuracy)
                .nextReviewAt(uv.getNextReviewAt())
                .intervalDays(uv.getIntervalDays() != null ? uv.getIntervalDays() : 1)
                .easeFactor(uv.getEaseFactor() != null ? uv.getEaseFactor().doubleValue() : 2.5)
                .build();
    }
}
