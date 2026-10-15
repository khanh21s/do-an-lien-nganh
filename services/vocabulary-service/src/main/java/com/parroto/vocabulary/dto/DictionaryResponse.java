package com.parroto.vocabulary.dto;

import java.util.List;

/**
 * P2-C: GET /vocabs/dictionary/{word}. Vietnamese meanings come from the app's own cards (free
 * public decks + the caller's decks), English definitions/IPA/audio from the Free Dictionary API.
 *
 * @param query          the normalised word that was asked for
 * @param word           the word the data is about (a lemma when a fallback matched: "running" → "run")
 * @param source         {@code cards}, {@code dictionary}, {@code cards+dictionary} or {@code none}
 * @param externalStatus {@code ok}, {@code miss} (API has no entry), {@code error} (API unreachable;
 *                       the response still carries what the cards know), {@code skipped} or {@code disabled}
 */
public record DictionaryResponse(
        String query,
        String word,
        boolean found,
        String ipa,
        String audioUrl,
        List<String> meaningsVi,
        List<String> partsOfSpeech,
        List<DefinitionEn> definitionsEn,
        List<Example> examples,
        String source,
        String externalStatus
) {
    public record DefinitionEn(String partOfSpeech, String definition, String example) {
    }

    /** Example sentence from a card ({@code vi} may be null). */
    public record Example(String en, String vi) {
    }
}
