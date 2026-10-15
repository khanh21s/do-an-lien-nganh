package com.parroto.practice.service.review;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Standard SM-2 (SuperMemo / Wikipedia), exactly docs/architecture.md §4.6 — a copy of
 * vocabulary-service's SM2Calculator so practice-service does not depend on it.
 *
 * <pre>
 * q >= 3: n == 0 -> I' = 1; n == 1 -> I' = 6; n > 1 -> I' = round(I * EF) (EF before the update); n' = n + 1
 * q <  3: n' = 0, I' = 1
 * EF' = max(1.3, EF + (0.1 - (5-q) * (0.08 + (5-q) * 0.02)))  rounded to 2 decimals HALF_UP
 * </pre>
 */
public final class SentenceSm2 {

    public static final BigDecimal MIN_EASE_FACTOR = new BigDecimal("1.30");
    public static final BigDecimal DEFAULT_EASE_FACTOR = new BigDecimal("2.50");

    private SentenceSm2() {
    }

    public record Result(int interval, BigDecimal easeFactor, int repetitions) {
    }

    public static Result calculate(int previousRepetitions, int previousIntervalDays, BigDecimal previousEaseFactor,
                                   int quality) {
        if (quality < 0 || quality > 5) {
            throw new IllegalArgumentException("quality must be between 0 and 5");
        }
        int n = Math.max(previousRepetitions, 0);
        int prevInterval = Math.max(previousIntervalDays, 1);
        BigDecimal ef = previousEaseFactor != null ? previousEaseFactor : DEFAULT_EASE_FACTOR;

        int interval;
        int repetitions;
        if (quality >= 3) {
            if (n == 0) {
                interval = 1;
            } else if (n == 1) {
                interval = 6;
            } else {
                interval = BigDecimal.valueOf(prevInterval).multiply(ef).setScale(0, RoundingMode.HALF_UP).intValue();
            }
            repetitions = n + 1;
        } else {
            interval = 1;
            repetitions = 0;
        }
        interval = Math.max(interval, 1);

        BigDecimal d = BigDecimal.valueOf(5 - quality);
        BigDecimal delta = new BigDecimal("0.1")
                .subtract(d.multiply(new BigDecimal("0.08").add(d.multiply(new BigDecimal("0.02")))));
        BigDecimal newEaseFactor = ef.add(delta).setScale(2, RoundingMode.HALF_UP);
        if (newEaseFactor.compareTo(MIN_EASE_FACTOR) < 0) {
            newEaseFactor = MIN_EASE_FACTOR;
        }
        return new Result(interval, newEaseFactor, repetitions);
    }
}
