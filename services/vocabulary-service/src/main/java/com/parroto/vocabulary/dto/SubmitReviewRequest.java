package com.parroto.vocabulary.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code POST /vocabs/user/review} (P2-anki).
 *
 * @param userVocabId the card's row (cards the user already has), or
 * @param cardId      a deck card; a card graded for the first time gets its row created
 * @param rating      1 Again, 2 Hard, 3 Good, 4 Easy
 * @param quality     Phase-1 clients: SM-2 quality 0–5 (used only when {@code rating} is missing)
 * @param answeredAt  optional client time of the answer; used only when it lies within the last
 *                    10 minutes (never in the future), otherwise the server time is used
 * @param deckId      optional session scope for the returned counters/next card ({@code groupId}, {@code seed}
 *                    as in {@code GET /vocabs/decks/{id}/study}); without it the global session is returned
 */
public record SubmitReviewRequest(
        UUID userVocabId,
        UUID cardId,
        @Min(1) @Max(4) Integer rating,
        @Min(0) @Max(5) Integer quality,
        OffsetDateTime answeredAt,
        UUID deckId,
        String groupId,
        String seed
) {
    /** Phase-1 shape {userVocabId, quality}. */
    public SubmitReviewRequest(UUID userVocabId, Integer quality) {
        this(userVocabId, null, null, quality, null, null, null, null);
    }
}
