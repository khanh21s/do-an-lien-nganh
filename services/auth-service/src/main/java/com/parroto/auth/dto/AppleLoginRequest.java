package com.parroto.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AppleLoginRequest(@NotBlank String appleToken) {
}
