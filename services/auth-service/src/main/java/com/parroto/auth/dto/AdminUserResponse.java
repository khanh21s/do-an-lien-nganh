package com.parroto.auth.dto;

import com.parroto.auth.entity.User;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String authProvider,
        String cefrLevel,
        boolean isActive,
        boolean isAdmin,
        OffsetDateTime createdAt,
        OffsetDateTime lastLoginAt
) {
    public static AdminUserResponse from(User user, boolean isAdmin) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getAuthProvider(), user.getCefrLevel(),
                Boolean.TRUE.equals(user.getIsActive()), isAdmin, user.getCreatedAt(), user.getLastLoginAt());
    }
}
