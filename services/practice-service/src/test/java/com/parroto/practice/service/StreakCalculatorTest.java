package com.parroto.practice.service;

import com.parroto.practice.util.AppTime;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StreakCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);
    private final StreakCalculator calculator = new StreakCalculator();

    @Test
    void noPracticeMeansNoStreak() {
        assertThat(calculator.calculate(List.of(), TODAY)).isEqualTo(new StreakCalculator.StreakResult(0, 0));
    }

    @Test
    void consecutiveDaysEndingTodayFormTheCurrentStreak() {
        var result = calculator.calculate(List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2)), TODAY);
        assertThat(result.currentStreak()).isEqualTo(3);
        assertThat(result.longestStreak()).isEqualTo(3);
    }

    @Test
    void streakEndingYesterdayIsStillAlive() {
        var result = calculator.calculate(List.of(TODAY.minusDays(1), TODAY.minusDays(2)), TODAY);
        assertThat(result.currentStreak()).isEqualTo(2);
    }

    @Test
    void brokenChainResetsCurrentButKeepsLongest() {
        var days = List.of(TODAY, TODAY.minusDays(1),
                TODAY.minusDays(5), TODAY.minusDays(6), TODAY.minusDays(7), TODAY.minusDays(8));
        var result = calculator.calculate(days, TODAY);
        assertThat(result.currentStreak()).isEqualTo(2);
        assertThat(result.longestStreak()).isEqualTo(4);
    }

    @Test
    void lastPracticeTwoDaysAgoMeansStreakIsLost() {
        var result = calculator.calculate(List.of(TODAY.minusDays(2), TODAY.minusDays(3)), TODAY);
        assertThat(result.currentStreak()).isZero();
        assertThat(result.longestStreak()).isEqualTo(2);
    }

    @Test
    void attemptAt2330VietnamTimeCountsForThatVietnamDay() {
        // 23:30 in Hanoi on Oct 12 is 16:30 UTC on Oct 12 -> still Oct 12 locally.
        OffsetDateTime lateEvening = OffsetDateTime.of(2026, 10, 12, 16, 30, 0, 0, ZoneOffset.UTC);
        // 00:30 in Hanoi on Oct 13 is 17:30 UTC on Oct 12 -> Oct 13 locally (UTC would say Oct 12).
        OffsetDateTime afterMidnight = OffsetDateTime.of(2026, 10, 12, 17, 30, 0, 0, ZoneOffset.UTC);

        assertThat(AppTime.localDate(lateEvening)).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(AppTime.localDate(afterMidnight)).isEqualTo(LocalDate.of(2026, 10, 13));

        var result = calculator.calculate(List.of(AppTime.localDate(afterMidnight), AppTime.localDate(lateEvening)), TODAY);
        assertThat(result.currentStreak()).isEqualTo(2);
    }

    @Test
    void unsortedAndDuplicateDaysAreHandled() {
        var result = calculator.calculate(List.of(TODAY.minusDays(1), TODAY, TODAY, TODAY.minusDays(2)), TODAY);
        assertThat(result.currentStreak()).isEqualTo(3);
    }
}
