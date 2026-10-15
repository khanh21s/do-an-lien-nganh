package com.parroto.practice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record DictationAttemptRequest(
        @NotNull UUID lessonId,
        @NotNull UUID sentenceId,
        @NotBlank String userInput,
        @Pattern(regexp = "Easy|Normal|Hard", message = "difficulty must be Easy, Normal or Hard")
        String difficulty,
        /** P2-A2: set by the review screen ("Ôn câu sai") to grade that queue item. */
        UUID reviewId
) {
}
