package com.parroto.subscription.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.parroto.subscription.entity.Plan;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Admin view of a plan, including disabled plans (T14-SUB). */
@Builder
public record AdminPlanResponse(
        String id,
        String name,
        int durationDays,
        long price,
        List<String> features,
        @JsonProperty("isActive") boolean isActive,
        long activeSubscribers,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AdminPlanResponse from(Plan plan, long activeSubscribers) {
        return AdminPlanResponse.builder()
                .id(plan.getId())
                .name(plan.getName())
                .durationDays(plan.getDurationDays())
                .price(plan.getPrice())
                .features(featureList(plan.getFeatures()))
                .isActive(plan.isActive())
                .activeSubscribers(activeSubscribers)
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }

    /** The {@code features} JSONB column holds a string array; anything else maps to an empty list. */
    static List<String> featureList(JsonNode features) {
        List<String> result = new ArrayList<>();
        if (features != null && features.isArray()) {
            features.forEach(node -> result.add(node.asText()));
        }
        return result;
    }
}
