package com.parroto.subscription.dto;

import lombok.Builder;

public record UserSubscriptionResponse(SubscriptionInfo subscription, boolean isPremium) {

    @Builder
    public record SubscriptionInfo(
            String id,
            String planId,
            String planName,
            String startAt,
            String endAt,
            String status,
            long daysRemaining,
            boolean autoRenew
    ) {
    }
}
