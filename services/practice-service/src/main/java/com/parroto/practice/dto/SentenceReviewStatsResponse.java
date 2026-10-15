package com.parroto.practice.dto;

import java.time.OffsetDateTime;

/** P2-A2 {@code GET /practice/review/stats}: due now, total in the queue, next future due time (or null). */
public record SentenceReviewStatsResponse(long dueCount, long total, OffsetDateTime nextDueAt) {
}
