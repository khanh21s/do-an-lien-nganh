package com.parroto.practice.service;

import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Component
public class StreakCalculator {

    public record StreakResult(int currentStreak, int longestStreak) {
    }

    /**
     * @param practiceDatesDesc distinct practice days, most recent first
     */
    public StreakResult calculate(List<Date> practiceDatesDesc) {
        if (practiceDatesDesc.isEmpty()) {
            return new StreakResult(0, 0);
        }
        List<LocalDate> days = practiceDatesDesc.stream().map(Date::toLocalDate).toList();

        LocalDate today = LocalDate.now();
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
