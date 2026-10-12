package com.parroto.content.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateSentencesRequest(
        @NotEmpty @Valid List<SentenceInput> sentences
) {
}
