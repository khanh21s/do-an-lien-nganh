package com.parroto.vocabulary.dto;

public record UpdateCardRequest(
        String meaning,
        String example,
        String exampleVi,
        String ipa,
        String audioUrl,
        String imageUrl,
        String partOfSpeech
) {
}
