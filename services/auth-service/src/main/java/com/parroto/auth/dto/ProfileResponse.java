package com.parroto.auth.dto;

import com.parroto.auth.entity.User;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record ProfileResponse(
        UUID userId,
        String email,
        String cefrLevel,
        String authProvider,
        BigDecimal totalHoursLearned,
        Integer totalWordsLearned,
        String avatarUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        boolean isAdmin
) {
    public static ProfileResponse from(User user, boolean isAdmin) {
        return ProfileResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .cefrLevel(user.getCefrLevel())
                .authProvider(user.getAuthProvider())
                .totalHoursLearned(user.getTotalHoursLearned())
                .totalWordsLearned(user.getTotalWordsLearned())
                .avatarUrl(user.getAvatarUrl())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .isAdmin(isAdmin)
                .build();
    }
}
