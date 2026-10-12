package com.parroto.auth.dto;

import jakarta.validation.constraints.Pattern;

public record UpdateProfileRequest(
        @Pattern(regexp = "A1|A2|B1|B2|C1|C2", message = "cefrLevel must be one of A1, A2, B1, B2, C1, C2")
        String cefrLevel,
        String avatarUrl
) {
}
