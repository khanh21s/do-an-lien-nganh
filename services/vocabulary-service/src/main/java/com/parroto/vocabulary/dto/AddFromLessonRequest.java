package com.parroto.vocabulary.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddFromLessonRequest(@NotNull UUID cardId, String source) {
}
