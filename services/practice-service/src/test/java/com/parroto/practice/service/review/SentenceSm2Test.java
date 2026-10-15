package com.parroto.practice.service.review;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Same expectations as vocabulary-service's SM2CalculatorTest (docs/architecture.md §4.6). */
class SentenceSm2Test {

    private static final BigDecimal EF = new BigDecimal("2.50");

    @Test
    void architectureExampleCard() {
        // n=3, I=10, EF=2.5
        assertThat(SentenceSm2.calculate(3, 10, EF, 1)).isEqualTo(new SentenceSm2.Result(1, new BigDecimal("1.96"), 0));
        assertThat(SentenceSm2.calculate(3, 10, EF, 3)).isEqualTo(new SentenceSm2.Result(25, new BigDecimal("2.36"), 4));
        assertThat(SentenceSm2.calculate(3, 10, EF, 4)).isEqualTo(new SentenceSm2.Result(25, new BigDecimal("2.50"), 4));
        assertThat(SentenceSm2.calculate(3, 10, EF, 5)).isEqualTo(new SentenceSm2.Result(25, new BigDecimal("2.60"), 4));
    }

    @Test
    void firstAndSecondRepetitions() {
        assertThat(SentenceSm2.calculate(0, 0, EF, 4).interval()).isEqualTo(1);
        assertThat(SentenceSm2.calculate(1, 1, EF, 4).interval()).isEqualTo(6);
        assertThat(SentenceSm2.calculate(2, 6, EF, 4).interval()).isEqualTo(15);
    }

    @Test
    void easeFactorNeverBelowFloor() {
        assertThat(SentenceSm2.calculate(0, 1, new BigDecimal("1.30"), 1).easeFactor()).isEqualByComparingTo("1.30");
        assertThat(SentenceSm2.calculate(0, 1, null, 4).easeFactor()).isEqualByComparingTo("2.50");
    }
}
