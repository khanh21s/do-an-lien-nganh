package com.parroto.subscription.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Body of {@code POST /subscriptions/admin/user-status} (T14-SUB). */
public record UserStatusRequest(
        @NotNull(message = "userIds là bắt buộc")
        @Size(max = 100, message = "Tối đa 100 userIds mỗi lần")
        List<UUID> userIds
) {
}
