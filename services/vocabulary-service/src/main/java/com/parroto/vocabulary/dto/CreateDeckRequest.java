package com.parroto.vocabulary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateDeckRequest(
        @NotBlank String name,
        String description,
        @Pattern(regexp = "A1|A2|B1|B2|C1|C2", message = "level must be one of A1, A2, B1, B2, C1, C2")
        String level,
        Boolean isProOnly
) {
}
