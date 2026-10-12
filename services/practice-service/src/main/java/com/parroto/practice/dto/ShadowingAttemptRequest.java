package com.parroto.practice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ShadowingAttemptRequest(
        @NotNull UUID lessonId,
        @NotNull UUID sentenceId,
        @NotBlank String recordingId,
        BigDecimal duration
) {
}
