package com.parroto.practice.service;

import com.parroto.practice.util.AppTime;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * PRA-07: a streak is the number of consecutive calendar days (Asia/Ho_Chi_Minh) with at
 * least one practice attempt. The current streak stays alive until the end of the day
 * after the last practice day (practising yesterday but not yet today still counts).
 */
@Component
public class StreakCalculator {

    public record StreakResult(int currentStreak, int longestStreak) {
    }

    /** @param practiceDaysDesc distinct practice days in Vietnam time, most recent first */
    public StreakResult calculate(List<LocalDate> practiceDaysDesc) {
        return calculate(practiceDaysDesc, AppTime.today());
    }

    /**
     * @param practiceDaysDesc distinct practice days in Vietnam time, most recent first
     * @param today            "today" in Vietnam time
     */
    public StreakResult calculate(List<LocalDate> practiceDaysDesc, LocalDate today) {
        if (practiceDaysDesc == null || practiceDaysDesc.isEmpty()) {
            return new StreakResult(0, 0);
        }
        List<LocalDate> days = practiceDaysDesc.stream()
                .filter(d -> !d.isAfter(today))
                .distinct()
                .sorted((a, b) -> b.compareTo(a))
                .toList();
        if (days.isEmpty()) {
            return new StreakResult(0, 0);
        }

        int currentStreak = 0;
        if (days.get(0).equals(today) || days.get(0).equals(today.minusDays(1))) {
            currentStreak = 1;
            for (int i = 1; i < days.size(); i++) {
                if (days.get(i - 1).minusDays(1).equals(days.get(i))) {
                    currentStreak++;
                } else {
                    break;
                }
            }
        }

        int longestStreak = 1;
        int running = 1;
        for (int i = 1; i < days.size(); i++) {
            if (days.get(i - 1).minusDays(1).equals(days.get(i))) {
                running++;
            } else {
                running = 1;
            }
            longestStreak = Math.max(longestStreak, running);
        }

        return new StreakResult(currentStreak, longestStreak);
    }
}
