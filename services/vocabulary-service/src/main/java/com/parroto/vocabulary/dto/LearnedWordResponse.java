package com.parroto.vocabulary.dto;

import com.parroto.vocabulary.entity.UserVocab;
import com.parroto.vocabulary.entity.VocabCard;
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
        /** Schedule (VOC-05, Anki since P2-anki): next due time, current interval in days (0 while new/learning), ease. */
        OffsetDateTime nextReviewAt,
        int intervalDays,
        double easeFactor,
        /** T13: true when the card lives in this user's personal deck (word added by the user). */
        boolean custom,
        String ipa,
        String partOfSpeech,
        String example,
        String exampleVi,
        /** P2-anki: NEW, LEARNING, REVIEW or RELEARNING. */
        String state,
        int lapses
) {
    /** Reads {@code uv.card} and {@code uv.card.deck}: fetch both with the query (no N+1). */
    public static LearnedWordResponse from(UserVocab uv) {
        VocabCard card = uv.getCard();
        double computedAccuracy = uv.getQuality() != null ? (uv.getQuality() >= 3 ? 100.0 : uv.getQuality() * 20.0) : 0.0;
        return LearnedWordResponse.builder()
                .userVocabId(uv.getId())
                .cardId(card.getId())
                .word(card.getWord())
                .meaning(card.getMeaning())
                .repetitions(uv.getRepetitions() != null ? uv.getRepetitions() : 0)
                .accuracy(computedAccuracy)
                .nextReviewAt(uv.getNextReviewAt())
                .intervalDays(uv.getIntervalDays() != null ? uv.getIntervalDays() : 0)
                .easeFactor(uv.getEaseFactor() != null ? uv.getEaseFactor().doubleValue() : 2.5)
                .custom(card.getDeck() != null && card.getDeck().isOwnedBy(uv.getUserId()))
                .ipa(card.getIpa())
                .partOfSpeech(card.getPartOfSpeech())
                .example(card.getExample())
                .exampleVi(card.getExampleVi())
                .state(uv.getState() != null ? uv.getState().name() : "NEW")
                .lapses(uv.getLapses() != null ? uv.getLapses() : 0)
                .build();
    }
}
