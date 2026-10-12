package com.parroto.vocabulary.dto;

import com.parroto.vocabulary.entity.VocabDeck;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record DeckResponse(
        UUID id,
        String name,
        String description,
        String level,
        boolean isProOnly,
        int cardCount,
        OffsetDateTime createdAt
) {
    public static DeckResponse from(VocabDeck deck) {
        return DeckResponse.builder()
                .id(deck.getId())
                .name(deck.getName())
                .description(deck.getDescription())
                .level(deck.getLevel())
                .isProOnly(Boolean.TRUE.equals(deck.getIsProOnly()))
                .cardCount(deck.getCardCount() != null ? deck.getCardCount() : 0)
                .createdAt(deck.getCreatedAt())
                .build();
    }
}
