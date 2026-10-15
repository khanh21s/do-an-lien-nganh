package com.parroto.vocabulary.service.srs;

import com.parroto.vocabulary.entity.CardState;
import com.parroto.vocabulary.entity.UserVocab;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static com.parroto.vocabulary.service.srs.AnkiDefaults.*;

/**
 * Anki's SM-2 scheduler (the "v3" answer rules with Anki's default deck options, constants in
 * {@link AnkiDefaults}). Pure: the result depends only on the card, the rating and {@code now}, so
 * the four button previews are computed with exactly the code that grading runs.
 *
 * <pre>
 * NEW / LEARNING (steps 1m, 10m; step i)
 *   Again → step 0 (1m)
 *   Hard  → stay on step i: delay = step i, on the first step the average of step 1 and 2 (≈6m)
 *   Good  → step i+1, or graduate after the last step: REVIEW, interval 1 day
 *   Easy  → graduate at once: REVIEW, interval 4 days
 *   (a NEW card starts with ease 2.50; learning answers never change the ease)
 * REVIEW (interval I, ease E, daysLate = today − due day ≥ 0)
 *   Again → lapse: E −0.20, lapses +1, RELEARNING step 0 (10m), interval kept for later = max(1, I × 0)
 *   Hard  → I × 1.2                       ≥ I + 1,     E −0.15
 *   Good  → (I + daysLate/2) × E          ≥ Hard + 1
 *   Easy  → (I + daysLate) × E × 1.3      ≥ Good + 1,  E +0.15
 *   answered before its due day (early): Anki's early-review formulas, see {@link #earlyIntervals}
 * RELEARNING (steps 10m)
 *   Again → step 0;  Hard → repeat the step (single step: × 1.5);  Good → next step or back to REVIEW
 *   with the kept interval;  Easy → REVIEW with the kept interval + 1 day
 * Always: ease ≥ 1.30, 1 ≤ interval ≤ 36500 days, review due = 04:00 (Vietnam) of the due day.
 * </pre>
 *
 * No interval fuzz (Anki adds a small random spread to intervals ≥ 3 days): left out on purpose so
 * the previews are exact and stable; see docs/reports/P2-anki.md.
 */
@Component
public class AnkiScheduler {

    /** What one button does to a card. {@code label} is the Vietnamese text shown on the button. */
    public record Outcome(Rating rating, SrsCard card, String label) {
    }

    public Outcome answer(SrsCard card, Rating rating, OffsetDateTime now) {
        SrsCard c = card == null ? SrsCard.newCard() : card;
        return switch (c.state()) {
            case NEW, LEARNING -> answerLearning(c, rating, now, LEARNING_STEPS, false);
            case RELEARNING -> answerLearning(c, rating, now, RELEARNING_STEPS, true);
            case REVIEW -> answerReview(c, rating, now);
        };
    }

    /**
     * Grades a stored card: applies the outcome to the entity and records the answer (answer count,
     * last answer as SM-2 quality, answer time). The caller saves the entity.
     */
    public Outcome grade(UserVocab uv, Rating rating, OffsetDateTime now) {
        Outcome outcome = answer(SrsCard.of(uv), rating, now);
        outcome.card().applyTo(uv);
        uv.setRepetitions((uv.getRepetitions() == null ? 0 : uv.getRepetitions()) + 1);
        uv.setQuality(rating.legacyQuality);
        uv.setLastReviewAt(now);
        return outcome;
    }

    /** The four outcomes in button order (Again, Hard, Good, Easy). */
    public List<Outcome> previews(SrsCard card, OffsetDateTime now) {
        List<Outcome> out = new ArrayList<>(4);
        for (Rating r : Rating.values()) {
            out.add(answer(card, r, now));
        }
        return out;
    }

    /** NEW cards are always available; learning cards at their timestamp; review cards from 04:00 of the due day. */
    public static boolean isDue(SrsCard card, OffsetDateTime now) {
        if (card.state() == CardState.NEW || card.due() == null) return true;
        if (card.state().isLearning()) return !card.due().isAfter(now);
        return card.due().isBefore(AnkiDay.nextDayStart(now));
    }

    // ------------------------------------------------------------------ learning / relearning

    private Outcome answerLearning(SrsCard c, Rating rating, OffsetDateTime now, List<Duration> steps, boolean relearning) {
        CardState stay = relearning ? CardState.RELEARNING : CardState.LEARNING;
        BigDecimal ease = c.state() == CardState.NEW ? STARTING_EASE : c.ease();
        int keptInterval = relearning ? Math.max(c.intervalDays(), LAPSE_MIN_INTERVAL_DAYS) : 0;
        int step = c.state() == CardState.NEW ? 0 : Math.min(c.step(), Math.max(steps.size() - 1, 0));

        if (steps.isEmpty()) {
            // No steps configured: every answer graduates (Again included), like Anki.
            int days = relearning ? keptInterval : (rating == Rating.EASY ? EASY_INTERVAL_DAYS : GRADUATING_INTERVAL_DAYS);
            return graduate(c, rating, ease, days, now);
        }
        return switch (rating) {
            case AGAIN -> stayLearning(c, rating, stay, 0, keptInterval, ease, steps.get(0), now);
            case HARD -> stayLearning(c, rating, stay, step, keptInterval, ease, hardDelay(steps, step), now);
            case GOOD -> step + 1 < steps.size()
                    ? stayLearning(c, rating, stay, step + 1, keptInterval, ease, steps.get(step + 1), now)
                    : graduate(c, rating, ease, relearning ? keptInterval : GRADUATING_INTERVAL_DAYS, now);
            case EASY -> graduate(c, rating, ease, relearning ? keptInterval + 1 : EASY_INTERVAL_DAYS, now);
        };
    }

    /** Anki: Hard repeats the current step; on the first step it waits the average of steps 1 and 2. */
    static Duration hardDelay(List<Duration> steps, int step) {
        Duration current = steps.get(step);
        if (step == 0) {
            if (steps.size() > 1) {
                return current.plus(steps.get(1)).dividedBy(2);
            }
            Duration oneAndHalf = current.multipliedBy(3).dividedBy(2);
            Duration capped = current.plusDays(1);
            return oneAndHalf.compareTo(capped) < 0 ? oneAndHalf : capped;
        }
        return current;
    }

    private Outcome stayLearning(SrsCard c, Rating rating, CardState state, int step, int interval, BigDecimal ease,
                                 Duration delay, OffsetDateTime now) {
        SrsCard next = new SrsCard(state, step, interval, ease, c.lapses(), now.plus(delay));
        return new Outcome(rating, next, IntervalLabels.duration(delay));
    }

    private Outcome graduate(SrsCard c, Rating rating, BigDecimal ease, int days, OffsetDateTime now) {
        int interval = clampInterval(days, 1);
        return new Outcome(rating, reviewCard(interval, ease, c.lapses(), now), IntervalLabels.days(interval));
    }

    // ------------------------------------------------------------------ review

    private Outcome answerReview(SrsCard c, Rating rating, OffsetDateTime now) {
        int current = Math.max(c.intervalDays(), 1);
        BigDecimal ease = c.ease();
        if (rating == Rating.AGAIN) {
            BigDecimal lapsedEase = adjustEase(ease, EASE_DELTA_AGAIN);
            int lapses = c.lapses() + 1;
            int kept = clampInterval((int) Math.round(current * LAPSE_NEW_INTERVAL_FACTOR), LAPSE_MIN_INTERVAL_DAYS);
            if (RELEARNING_STEPS.isEmpty()) {
                return new Outcome(rating, reviewCard(kept, lapsedEase, lapses, now), IntervalLabels.days(kept));
            }
            Duration delay = RELEARNING_STEPS.get(0);
            SrsCard next = new SrsCard(CardState.RELEARNING, 0, kept, lapsedEase, lapses, now.plus(delay));
            return new Outcome(rating, next, IntervalLabels.duration(delay));
        }

        long daysLate = c.due() == null ? 0 : ChronoUnit.DAYS.between(AnkiDay.dayOf(c.due()), AnkiDay.dayOf(now));
        int[] ivl = daysLate < 0
                ? earlyIntervals(current, current + daysLate, ease.doubleValue())
                : passingIntervals(current, daysLate, ease.doubleValue());
        int interval;
        BigDecimal newEase;
        switch (rating) {
            case HARD -> {
                interval = ivl[0];
                newEase = adjustEase(ease, EASE_DELTA_HARD);
            }
            case GOOD -> {
                interval = ivl[1];
                newEase = ease;
            }
            default -> {
                interval = ivl[2];
                newEase = adjustEase(ease, EASE_DELTA_EASY);
            }
        }
        return new Outcome(rating, reviewCard(interval, newEase, c.lapses(), now), IntervalLabels.days(interval));
    }

    /**
     * Hard/Good/Easy intervals of a review card answered on or after its due day, with Anki's
     * ordering rule Hard ≥ I+1, Good ≥ Hard+1, Easy ≥ Good+1 (each still capped at the maximum).
     */
    static int[] passingIntervals(int current, long daysLate, double ease) {
        int hard = constrain(current * HARD_INTERVAL_FACTOR, current + 1);
        int good = constrain((current + daysLate / 2.0) * ease, hard + 1);
        int easy = constrain((current + daysLate) * ease * EASY_BONUS, good + 1);
        return new int[]{hard, good, easy};
    }

    /**
     * Anki's early review (answered before the due day, e.g. in a game): based on the days that
     * really elapsed since the last review, never shorter than half the usual Hard factor / the
     * scheduled interval, and with half the easy bonus.
     */
    static int[] earlyIntervals(int scheduled, long elapsedDays, double ease) {
        double elapsed = Math.max(elapsedDays, 0);
        int hard = constrain(Math.max(elapsed * HARD_INTERVAL_FACTOR, scheduled * HARD_INTERVAL_FACTOR / 2.0), 0);
        double goodRaw = Math.max(elapsed * ease, scheduled);
        int good = constrain(goodRaw, 0);
        double reducedBonus = EASY_BONUS - (EASY_BONUS - 1.0) / 2.0;
        int easy = constrain(goodRaw * reducedBonus, 0);
        return new int[]{hard, good, easy};
    }

    /** Anki's constrain_passing_interval without fuzz: × interval modifier, round, clamp [max(min,1), max]. */
    static int constrain(double interval, int minimum) {
        long rounded = Math.round(interval * INTERVAL_MODIFIER);
        int min = Math.min(Math.max(minimum, 1), MAX_INTERVAL_DAYS);
        return (int) Math.max(min, Math.min(rounded, MAX_INTERVAL_DAYS));
    }

    private static int clampInterval(int days, int minimum) {
        return constrain(days, minimum);
    }

    static BigDecimal adjustEase(BigDecimal ease, BigDecimal delta) {
        BigDecimal next = ease.add(delta).setScale(2, RoundingMode.HALF_UP);
        return next.compareTo(MIN_EASE) < 0 ? MIN_EASE : next;
    }

    private static SrsCard reviewCard(int interval, BigDecimal ease, int lapses, OffsetDateTime now) {
        LocalDate dueDay = AnkiDay.dayOf(now).plusDays(interval);
        return new SrsCard(CardState.REVIEW, 0, interval, ease.setScale(2, RoundingMode.HALF_UP), lapses,
                AnkiDay.startOf(dueDay));
    }
}
