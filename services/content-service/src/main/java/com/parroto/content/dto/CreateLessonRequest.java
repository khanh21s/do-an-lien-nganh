package com.parroto.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateLessonRequest(
        @NotBlank String title,
        String sourceUrl,
        @Pattern(regexp = "A1|A2|B1|B2|C1|C2", message = "cefrLevel must be one of A1, A2, B1, B2, C1, C2")
        String cefrLevel,
        String topicTag,
        String description,
        @NotBlank String audioUrl,
        Integer duration,
        /** P2-A1: optional, null → false. */
        Boolean isPro
) {
}
