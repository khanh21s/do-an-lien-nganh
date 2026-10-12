package com.parroto.vocabulary.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * SM-2 spaced repetition algorithm, implemented exactly per the project spec
 * (https://en.wikipedia.org/wiki/Spaced_repetition#SM-2):
 *
 *   repetitions == 0 -> interval = 1
 *   repetitions == 1 -> interval = 3
 *   repetitions  > 1 -> interval = round(previousInterval * easeFactor)
 *   newEaseFactor = easeFactor + (0.1 - (5-quality) * (0.08 + (5-quality) * 0.02)), floor 1.3
 */
@Component
public class SM2Calculator {

    private static final BigDecimal MIN_EASE_FACTOR = BigDecimal.valueOf(1.3);

    public record Sm2Result(int interval, BigDecimal easeFactor) {
    }

    public Sm2Result calculate(int previousRepetitions, int previousIntervalDays, BigDecimal previousEaseFactor, int quality) {
        int interval;
        if (previousRepetitions == 0) {
            interval = 1;
        } else if (previousRepetitions == 1) {
            interval = 3;
        } else {
            interval = (int) Math.round(previousIntervalDays * previousEaseFactor.doubleValue());
        }

        double q = quality;
        double delta = 0.1 - (5 - q) * (0.08 + (5 - q) * 0.02);
        BigDecimal newEaseFactor = previousEaseFactor.add(BigDecimal.valueOf(delta)).setScale(2, RoundingMode.HALF_UP);
        if (newEaseFactor.compareTo(MIN_EASE_FACTOR) < 0) {
            newEaseFactor = MIN_EASE_FACTOR;
        }

        return new Sm2Result(interval, newEaseFactor);
    }
}
