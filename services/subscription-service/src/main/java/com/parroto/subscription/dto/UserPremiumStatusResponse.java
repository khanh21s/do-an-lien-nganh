package com.parroto.subscription.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One entry of {@code POST /subscriptions/admin/user-status}; {@code planId}/{@code endAt} null when not premium. */
public record UserPremiumStatusResponse(UUID userId, boolean premium, String planId, OffsetDateTime endAt) {
}
