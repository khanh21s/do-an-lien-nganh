package com.parroto.vocabulary.dto;

/**
 * T14-VOC: full replacement of a public deck's metadata (PUT /vocabs/decks/{id}).
 * Validated in {@code DeckService.updateDeck} so failures are 400 with Vietnamese messages
 * (bean-validation errors map to 422 in this service).
 */
public record UpdateDeckRequest(
        String name,
        String description,
        String level,
        Boolean isProOnly
) {
}
