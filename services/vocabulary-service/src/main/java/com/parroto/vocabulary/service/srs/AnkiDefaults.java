package com.parroto.vocabulary.service.srs;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

/**
 * Every constant of the Anki-style vocabulary scheduler (docs/architecture.md §4.6, docs/reports/P2-anki.md).
 * The values are Anki's defaults for a new deck preset (SM-2 / "v3" scheduler, FSRS off):
 *
 * <table>
 *   <tr><th>Anki option</th><th>Value</th></tr>
 *   <tr><td>Learning steps</td><td>1m 10m</td></tr>
 *   <tr><td>Graduating interval</td><td>1 day</td></tr>
 *   <tr><td>Easy interval</td><td>4 days</td></tr>
 *   <tr><td>Starting ease</td><td>2.50</td></tr>
 *   <tr><td>Easy bonus</td><td>1.30</td></tr>
 *   <tr><td>Hard interval</td><td>1.20</td></tr>
 *   <tr><td>Interval modifier</td><td>1.00</td></tr>
 *   <tr><td>Relearning steps</td><td>10m</td></tr>
 *   <tr><td>New interval (after a lapse)</td><td>0.00 (0 % of the old interval)</td></tr>
 *   <tr><td>Minimum interval (after a lapse)</td><td>1 day</td></tr>
 *   <tr><td>Maximum interval</td><td>36500 days</td></tr>
 *   <tr><td>Ease floor</td><td>1.30 (Anki hard-codes it)</td></tr>
 *   <tr><td>Ease change Again / Hard / Easy (review cards)</td><td>−0.20 / −0.15 / +0.15</td></tr>
 *   <tr><td>Next day starts at</td><td>4:00, Asia/Ho_Chi_Minh</td></tr>
 *   <tr><td>Learn ahead limit</td><td>20 minutes</td></tr>
 *   <tr><td>Daily limits</td><td>none (user decision)</td></tr>
 *   <tr><td>Interval fuzz</td><td>off (labels and tests stay exact; see the P2-anki report)</td></tr>
 * </table>
 */
public final class AnkiDefaults {

    private AnkiDefaults() {
    }

    public static final List<Duration> LEARNING_STEPS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(10));
    public static final List<Duration> RELEARNING_STEPS = List.of(Duration.ofMinutes(10));

    public static final int GRADUATING_INTERVAL_DAYS = 1;
    public static final int EASY_INTERVAL_DAYS = 4;

    public static final BigDecimal STARTING_EASE = new BigDecimal("2.50");
    public static final BigDecimal MIN_EASE = new BigDecimal("1.30");
    public static final BigDecimal EASE_DELTA_AGAIN = new BigDecimal("-0.20");
    public static final BigDecimal EASE_DELTA_HARD = new BigDecimal("-0.15");
    public static final BigDecimal EASE_DELTA_EASY = new BigDecimal("0.15");

    public static final double EASY_BONUS = 1.30;
    public static final double HARD_INTERVAL_FACTOR = 1.20;
    public static final double INTERVAL_MODIFIER = 1.00;

    /** "New interval" of a lapsed review card, as a fraction of its old interval. */
    public static final double LAPSE_NEW_INTERVAL_FACTOR = 0.0;
    public static final int LAPSE_MIN_INTERVAL_DAYS = 1;
    public static final int MAX_INTERVAL_DAYS = 36500;

    /** Day-based due dates: an Anki "day" runs from 04:00 to 04:00 Vietnam time. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final int DAY_START_HOUR = 4;

    /** A learning card due within this window may be shown early when nothing else is left. */
    public static final Duration LEARN_AHEAD = Duration.ofMinutes(20);
}
