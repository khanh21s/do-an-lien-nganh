package com.parroto.subscription.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePaymentRequest(@NotBlank String planId) {
}
