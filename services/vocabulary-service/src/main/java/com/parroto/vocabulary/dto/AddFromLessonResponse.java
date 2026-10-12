package com.parroto.vocabulary.dto;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record AddFromLessonResponse(
        UUID userVocabId,
        UUID cardId,
        String word,
        String meaning,
        OffsetDateTime nextReviewAt,
        String status
) {
}
