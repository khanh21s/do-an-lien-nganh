package com.parroto.content.dto;

import jakarta.validation.constraints.NotNull;

public record SentenceInput(
        @NotNull String text,
        String ipa,
        String translationVi,
        @NotNull Integer startMs,
        @NotNull Integer endMs,
        String audioUrl,
        String imageUrl
) {
}
