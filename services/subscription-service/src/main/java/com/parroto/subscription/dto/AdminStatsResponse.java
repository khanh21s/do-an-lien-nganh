package com.parroto.subscription.dto;

import java.util.List;

/**
 * {@code GET /subscriptions/admin/stats} (T14-SUB). Revenue = sum of {@code amount} of {@code success}
 * transactions, excluding {@code gift_code}; {@code revenueByDay} always has 30 entries (oldest first,
 * last = today in Asia/Ho_Chi_Minh) and sums to {@code revenueLast30Days}.
 */
public record AdminStatsResponse(
        long activeSubscribers,
        long revenueTotal,
        long revenueLast30Days,
        long successfulTransactions,
        long pendingTransactions,
        List<RevenueDay> revenueByDay
) {
    /** @param date {@code YYYY-MM-DD} (Vietnam time) */
    public record RevenueDay(String date, long amount) {
    }
}
