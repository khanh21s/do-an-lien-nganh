package com.parroto.vocabulary.service.dictionary;

import java.util.List;

/**
 * P2-C: what the Free Dictionary API told us about one word, reduced to what the app shows.
 * Serialized to JSON in Redis ({@code vocab:dict:ext:v1:{word}}).
 */
public record ExternalDictionaryEntry(
        String word,
        String ipa,
        String audioUrl,
        List<Definition> definitions
) {
    public record Definition(String partOfSpeech, String definition, String example) {
    }
}
