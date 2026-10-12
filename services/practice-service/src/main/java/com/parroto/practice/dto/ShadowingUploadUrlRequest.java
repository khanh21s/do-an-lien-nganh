package com.parroto.practice.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ShadowingUploadUrlRequest(@NotNull UUID lessonId, @NotNull UUID sentenceId) {
}
