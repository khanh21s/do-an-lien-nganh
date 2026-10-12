package com.parroto.vocabulary.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SubmitReviewRequest(
        @NotNull UUID userVocabId,
        @NotNull @Min(0) @Max(5) Integer quality
) {
}
