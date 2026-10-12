package com.parroto.subscription.dto;

public record CreatePaymentResponse(String transactionId, String paymentUrl, long expiresIn, long amount) {
}
