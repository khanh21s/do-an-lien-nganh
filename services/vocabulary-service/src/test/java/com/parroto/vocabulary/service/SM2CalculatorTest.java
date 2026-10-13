package com.parroto.vocabulary.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SM2CalculatorTest {

    private final SM2Calculator calc = new SM2Calculator();

    private static BigDecimal ef(String v) {
        return new BigDecimal(v);
    }

    /** Example from docs/architecture.md §4.6: card n=3, I=10, EF=2.5. */
    @ParameterizedTest(name = "n=3,I=10,EF=2.5 q={0} -> I={1}, n={2}, EF={3}")
    @CsvSource({
            "1, 1,  0, 1.96",   // Again
            "3, 25, 4, 2.36",   // Hard
            "4, 25, 4, 2.50",   // Good
            "5, 25, 4, 2.60",   // Easy
    })
    void matureCard(int quality, int interval, int reps, String easeFactor) {
        SM2Calculator.Sm2Result r = calc.calculate(3, 10, ef("2.50"), quality);
        assertThat(r.interval()).isEqualTo(interval);
        assertThat(r.repetitions()).isEqualTo(reps);
        assertThat(r.easeFactor()).isEqualByComparingTo(easeFactor);
    }

    @ParameterizedTest(name = "new card n=0 q={0} -> I={1}, n={2}, EF={3}")
    @CsvSource({
            "1, 1, 0, 1.96",
            "3, 1, 1, 2.36",
            "4, 1, 1, 2.50",
            "5, 1, 1, 2.60",
    })
    void newCard(int quality, int interval, int reps, String easeFactor) {
        SM2Calculator.Sm2Result r = calc.calculate(0, 1, ef("2.50"), quality);
        assertThat(r.interval()).isEqualTo(interval);
        assertThat(r.repetitions()).isEqualTo(reps);
        assertThat(r.easeFactor()).isEqualByComparingTo(easeFactor);
    }

    @ParameterizedTest(name = "n=1 q={0} -> I={1}, n={2}, EF={3}")
    @CsvSource({
            "1, 1, 0, 1.96",
            "3, 6, 2, 2.36",
            "4, 6, 2, 2.50",
            "5, 6, 2, 2.60",
    })
    void secondReview(int quality, int interval, int reps, String easeFactor) {
        SM2Calculator.Sm2Result r = calc.calculate(1, 1, ef("2.50"), quality);
        assertThat(r.interval()).isEqualTo(interval);
        assertThat(r.repetitions()).isEqualTo(reps);
        assertThat(r.easeFactor()).isEqualByComparingTo(easeFactor);
    }

    @Test
    void easeFactorNeverDropsBelowFloor() {
        SM2Calculator.Sm2Result r = calc.calculate(5, 30, ef("1.40"), SM2Calculator.QUALITY_AGAIN);
        assertThat(r.easeFactor()).isEqualByComparingTo("1.30");
        r = calc.calculate(5, 30, ef("1.30"), 0);
        assertThat(r.easeFactor()).isEqualByComparingTo("1.30");
        r = calc.calculate(5, 30, ef("1.30"), SM2Calculator.QUALITY_HARD);
        assertThat(r.easeFactor()).isEqualByComparingTo("1.30");
    }

    @Test
    void usesEaseFactorBeforeUpdateForInterval() {
        // round(10 * 1.3) = 13, computed with the EF before Hard lowers it (floored at 1.3)
        SM2Calculator.Sm2Result r = calc.calculate(2, 10, ef("1.30"), SM2Calculator.QUALITY_HARD);
        assertThat(r.interval()).isEqualTo(13);
        assertThat(r.repetitions()).isEqualTo(3);
    }

    @Test
    void againResetsEvenForLongIntervals() {
        SM2Calculator.Sm2Result r = calc.calculate(8, 200, ef("2.80"), SM2Calculator.QUALITY_AGAIN);
        assertThat(r.interval()).isEqualTo(1);
        assertThat(r.repetitions()).isZero();
        assertThat(r.easeFactor()).isEqualByComparingTo("2.26");
    }

    @Test
    void rejectsOutOfRangeQuality() {
        assertThatThrownBy(() -> calc.calculate(0, 1, ef("2.50"), 6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calc.calculate(0, 1, ef("2.50"), -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
