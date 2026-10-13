package com.parroto.subscription.dto;

import com.parroto.subscription.entity.ActivationCode;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

/** {@code ActivationCode} of architecture §4.5. */
@Builder
public record ActivationCodeResponse(
        UUID id,
        String code,
        String planId,
        String planName,
        UUID usedByUserId,
        String usedByEmail,
        OffsetDateTime usedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt
) {
    public static ActivationCodeResponse from(ActivationCode code, String usedByEmail) {
        return ActivationCodeResponse.builder()
                .id(code.getId())
                .code(code.getCode())
                .planId(code.getPlan().getId())
                .planName(code.getPlan().getName())
                .usedByUserId(code.getUsedByUserId())
                .usedByEmail(usedByEmail)
                .usedAt(code.getUsedAt())
                .expiresAt(code.getExpiresAt())
                .createdAt(code.getCreatedAt())
                .build();
    }
}
