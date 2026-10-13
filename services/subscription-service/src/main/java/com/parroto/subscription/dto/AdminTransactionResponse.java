package com.parroto.subscription.dto;

import com.parroto.subscription.entity.PaymentTransaction;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Row of {@code GET /subscriptions/admin/transactions} (T14-SUB). */
@Builder
public record AdminTransactionResponse(
        UUID id,
        UUID userId,
        String planId,
        String planName,
        long amount,
        String method,
        String status,
        String transactionId,
        String description,
        OffsetDateTime createdAt
) {
    public static AdminTransactionResponse from(PaymentTransaction tx, String planName) {
        return AdminTransactionResponse.builder()
                .id(tx.getId())
                .userId(tx.getUserId())
                .planId(tx.getPlanId())
                .planName(planName)
                .amount(tx.getAmount() == null ? 0 : tx.getAmount())
                .method(tx.getMethod())
                .status(tx.getStatus())
                .transactionId(tx.getTransactionId())
                .description(tx.getDescription())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
