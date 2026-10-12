package com.parroto.vocabulary.dto;

import jakarta.validation.constraints.NotBlank;

public record CardInput(
        @NotBlank String word,
        @NotBlank String meaning,
        String example,
        String exampleVi,
        String ipa,
        String audioUrl,
        String imageUrl,
        String partOfSpeech
) {
}
