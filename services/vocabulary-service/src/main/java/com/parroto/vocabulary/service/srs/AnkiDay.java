package com.parroto.vocabulary.service.srs;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import static com.parroto.vocabulary.service.srs.AnkiDefaults.DAY_START_HOUR;
import static com.parroto.vocabulary.service.srs.AnkiDefaults.ZONE;

/** Anki days: a day starts at 04:00 Asia/Ho_Chi_Minh ("next day starts at"), not at midnight. */
public final class AnkiDay {

    private AnkiDay() {
    }

    /** The Anki day a timestamp belongs to (03:59 VN still counts as the previous day). */
    public static LocalDate dayOf(OffsetDateTime t) {
        return t.atZoneSameInstant(ZONE).minusHours(DAY_START_HOUR).toLocalDate();
    }

    /** 04:00 Vietnam time on {@code day}, as an offset timestamp. */
    public static OffsetDateTime startOf(LocalDate day) {
        return day.atTime(LocalTime.of(DAY_START_HOUR, 0)).atZone(ZONE).toOffsetDateTime();
    }

    /** Start of the next Anki day after {@code now}: review cards due before it are due "today". */
    public static OffsetDateTime nextDayStart(OffsetDateTime now) {
        return startOf(dayOf(now).plusDays(1));
    }
}
