package com.parroto.vocabulary.service.srs;

import java.time.Duration;

/**
 * Vietnamese button labels, Anki style: "&lt;1 phút", "6 phút", "10 phút", "1,5 giờ", "4 ngày",
 * "1,2 tháng", "2,1 năm". Months are 365/12 days and years 365 days (as in Anki); one decimal
 * with a decimal comma, a trailing ",0" is dropped.
 */
public final class IntervalLabels {

    private static final double DAYS_PER_MONTH = 365.0 / 12.0;
    private static final double DAYS_PER_YEAR = 365.0;

    private IntervalLabels() {
    }

    /** Learning delays (minute steps). One minute or less reads "&lt;1 phút", like Anki's "&lt;1m". */
    public static String duration(Duration delay) {
        long seconds = Math.max(0, delay.getSeconds());
        if (seconds <= 60) return "<1 phút";
        if (seconds < 3600) return Math.round(seconds / 60.0) + " phút";
        if (seconds < 86_400) return oneDecimal(seconds / 3600.0) + " giờ";
        return days((int) Math.round(seconds / 86_400.0));
    }

    /** Review intervals in days. */
    public static String days(int days) {
        if (days < DAYS_PER_MONTH) return days + " ngày";
        if (days < DAYS_PER_YEAR) return oneDecimal(days / DAYS_PER_MONTH) + " tháng";
        return oneDecimal(days / DAYS_PER_YEAR) + " năm";
    }

    private static String oneDecimal(double value) {
        long tenths = Math.round(value * 10);
        long whole = tenths / 10;
        long fraction = tenths % 10;
        return fraction == 0 ? Long.toString(whole) : whole + "," + fraction;
    }
}
