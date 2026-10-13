package com.parroto.content.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Partial update of a sentence (ADM-03): every field is optional, {@code null} = keep current value.
 * For the optional text fields (ipa, translationVi, audioUrl, imageUrl) an empty string clears the value.
 */
public record UpdateSentenceRequest(
        String text,
        @Size(max = 255, message = "ipa tối đa 255 ký tự") String ipa,
        String translationVi,
        @Min(value = 0, message = "startMs phải >= 0") Integer startMs,
        @Min(value = 0, message = "endMs phải >= 0") Integer endMs,
        @Min(value = 1, message = "sentenceOrder phải >= 1") Integer sentenceOrder,
        String audioUrl,
        String imageUrl
) {
}
