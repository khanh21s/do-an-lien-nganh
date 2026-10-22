package com.parroto.vocabulary.dto;

import java.util.List;

/** A local card match used for fast, live dictionary suggestions. */
public record DictionarySuggestion(String word, List<String> meaningsVi, List<String> partsOfSpeech) {
}
