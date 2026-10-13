package com.parroto.practice.util;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Single source of truth for "which calendar day is it" in practice-service (PRA-07):
 * streaks, heatmap buckets and "today" are all computed in Vietnam time.
 */
public final class AppTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Postgres time zone name matching {@link #ZONE}, for native queries. */
    public static final String DB_ZONE = "Asia/Ho_Chi_Minh";

    private AppTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }

    /** Calendar day (Vietnam time) on which the given instant falls. */
    public static LocalDate localDate(OffsetDateTime instant) {
        return instant.atZoneSameInstant(ZONE).toLocalDate();
    }
}
