package com.parroto.subscription.dto;

import jakarta.validation.constraints.NotBlank;

public record ActivateCodeRequest(@NotBlank String code) {
}
