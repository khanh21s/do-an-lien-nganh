package com.parroto.auth.dto;

public record AdminUserStatsResponse(
        long totalUsers,
        long activeUsers,
        long lockedUsers,
        long newUsersLast7Days,
        long newUsersLast30Days
) {
}
