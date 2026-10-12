package com.parroto.subscription.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.parroto.subscription.entity.Plan;
import lombok.Builder;

import java.text.NumberFormat;
import java.util.Locale;

@Builder
public record PlanResponse(
        String id,
        String name,
        int durationDays,
        long price,
        JsonNode features,
        String priceDisplay
) {
    public static PlanResponse from(Plan plan) {
        NumberFormat vndFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        return PlanResponse.builder()
                .id(plan.getId())
                .name(plan.getName())
                .durationDays(plan.getDurationDays())
                .price(plan.getPrice())
                .features(plan.getFeatures())
                .priceDisplay(vndFormat.format(plan.getPrice()) + " ₫")
                .build();
    }
}
