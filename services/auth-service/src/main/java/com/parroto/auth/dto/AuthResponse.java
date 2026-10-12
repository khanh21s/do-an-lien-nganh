package com.parroto.auth.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record AuthResponse(
        UUID userId,
        String email,
        String cefrLevel,
        String token,
        String refreshToken,
        long expiresIn
) {
}
