package com.parroto.subscription.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * @param expiresAt optional ISO-8601 date-time with offset ({@code 2027-12-31T23:59:59+07:00}) or
 *                  plain date ({@code 2027-12-31}, end of that day in Asia/Ho_Chi_Minh); default now + 365 days
 */
public record CreateActivationCodesRequest(
        @NotBlank(message = "planId là bắt buộc") String planId,
        @NotNull(message = "quantity là bắt buộc")
        @Min(value = 1, message = "quantity phải từ 1 đến 500")
        @Max(value = 500, message = "quantity phải từ 1 đến 500") Integer quantity,
        String expiresAt
) {
}
