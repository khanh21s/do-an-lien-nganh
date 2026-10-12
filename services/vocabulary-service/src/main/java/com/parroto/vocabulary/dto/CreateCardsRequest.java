package com.parroto.vocabulary.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateCardsRequest(@NotEmpty @Valid List<CardInput> cards) {
}
