package com.parroto.subscription.dto;

import com.parroto.subscription.entity.PaymentTransaction;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record TransactionResponse(
        UUID id,
        long amount,
        String method,
        String status,
        OffsetDateTime createdAt,
        String planId
) {
    public static TransactionResponse from(PaymentTransaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .amount(tx.getAmount())
                .method(tx.getMethod())
                .status(tx.getStatus())
                .createdAt(tx.getCreatedAt())
                .planId(tx.getPlanId())
                .build();
    }
}
