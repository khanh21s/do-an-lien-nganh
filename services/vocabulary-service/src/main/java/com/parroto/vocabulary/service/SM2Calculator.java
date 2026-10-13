package com.parroto.vocabulary.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Standard SM-2 spaced repetition (SuperMemo / Wikipedia), per docs/architecture.md §4.6.
 *
 * <pre>
 * q >= 3 (Hard/Good/Easy):
 *     n == 0 -> I' = 1
 *     n == 1 -> I' = 6
 *     n  > 1 -> I' = round(I * EF)     (EF before the update)
 *     n' = n + 1
 * q < 3 (Again):
 *     n' = 0, I' = 1
 * EF' = max(1.3, EF + (0.1 - (5-q) * (0.08 + (5-q) * 0.02)))  rounded to 2 decimals HALF_UP
 * </pre>
 *
 * The 4 UI buttons map to quality Again=1, Hard=3, Good=4, Easy=5.
 */
@Component
public class SM2Calculator {

    public static final int QUALITY_AGAIN = 1;
    public static final int QUALITY_HARD = 3;
    public static final int QUALITY_GOOD = 4;
    public static final int QUALITY_EASY = 5;

    static final BigDecimal MIN_EASE_FACTOR = new BigDecimal("1.30");
    static final BigDecimal DEFAULT_EASE_FACTOR = new BigDecimal("2.50");

    public record Sm2Result(int interval, BigDecimal easeFactor, int repetitions) {
    }

    public Sm2Result calculate(int previousRepetitions, int previousIntervalDays, BigDecimal previousEaseFactor, int quality) {
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

        // Exact decimal arithmetic: 0.1 - d * (0.08 + d * 0.02), d = 5 - q
        BigDecimal d = BigDecimal.valueOf(5 - quality);
        BigDecimal delta = new BigDecimal("0.1")
                .subtract(d.multiply(new BigDecimal("0.08").add(d.multiply(new BigDecimal("0.02")))));
        BigDecimal newEaseFactor = ef.add(delta).setScale(2, RoundingMode.HALF_UP);
        if (newEaseFactor.compareTo(MIN_EASE_FACTOR) < 0) {
            newEaseFactor = MIN_EASE_FACTOR;
        }
        return new Sm2Result(interval, newEaseFactor, repetitions);
    }
}
