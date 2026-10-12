package com.parroto.vocabulary.dto;

import com.parroto.vocabulary.entity.UserVocab;
import lombok.Builder;

import java.util.UUID;

@Builder
public record LearnedWordResponse(
        UUID userVocabId,
        UUID cardId,
        String word,
        String meaning,
        int repetitions,
        double accuracy
) {
    public static LearnedWordResponse from(UserVocab uv) {
        double computedAccuracy = uv.getQuality() != null ? (uv.getQuality() >= 3 ? 100.0 : uv.getQuality() * 20.0) : 0.0;
        return LearnedWordResponse.builder()
                .userVocabId(uv.getId())
                .cardId(uv.getCard().getId())
                .word(uv.getCard().getWord())
                .meaning(uv.getCard().getMeaning())
                .repetitions(uv.getRepetitions())
                .accuracy(computedAccuracy)
                .build();
    }
}
