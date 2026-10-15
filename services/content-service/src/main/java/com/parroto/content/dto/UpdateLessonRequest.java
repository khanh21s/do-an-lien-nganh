package com.parroto.content.dto;

import jakarta.validation.constraints.Pattern;

public record UpdateLessonRequest(
        String title,
        String description,
        String topicTag,
        @Pattern(regexp = "A1|A2|B1|B2|C1|C2", message = "cefrLevel must be one of A1, A2, B1, B2, C1, C2")
        String cefrLevel,
        /** P2-A1: optional, null → unchanged. */
        Boolean isPro
) {
}
