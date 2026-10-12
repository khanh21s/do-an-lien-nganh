package com.parroto.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
        @Pattern(regexp = "A1|A2|B1|B2|C1|C2", message = "cefrLevel must be one of A1, A2, B1, B2, C1, C2")
        String cefrLevel
) {
}
