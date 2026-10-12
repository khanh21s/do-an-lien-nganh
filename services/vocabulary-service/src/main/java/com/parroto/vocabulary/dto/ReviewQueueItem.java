package com.parroto.vocabulary.dto;

import com.parroto.vocabulary.entity.UserVocab;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record ReviewQueueItem(
        UUID id,
        UUID cardId,
        String word,
        String meaning,
        String example,
        String ipa,
        OffsetDateTime lastReviewAt,
        OffsetDateTime nextReviewAt,
        int repetitions,
        double easeFactor,
        int intervalDays
) {
    public static ReviewQueueItem from(UserVocab uv) {
        return ReviewQueueItem.builder()
                .id(uv.getId())
                .cardId(uv.getCard().getId())
                .word(uv.getCard().getWord())
                .meaning(uv.getCard().getMeaning())
                .example(uv.getCard().getExample())
                .ipa(uv.getCard().getIpa())
                .lastReviewAt(uv.getLastReviewAt())
                .nextReviewAt(uv.getNextReviewAt())
                .repetitions(uv.getRepetitions())
                .easeFactor(uv.getEaseFactor().doubleValue())
                .intervalDays(uv.getIntervalDays())
                .build();
    }
}
