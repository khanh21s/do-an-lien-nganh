package com.parroto.practice.service;

import com.parroto.practice.dto.*;
import com.parroto.practice.entity.PracticeAttempt;
import com.parroto.practice.repository.PracticeAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private static final Duration STATS_TTL = Duration.ofHours(1);

    private final PracticeAttemptRepository attemptRepository;
    private final StreakCalculator streakCalculator;
    private final CacheService cacheService;

    public StatsService(PracticeAttemptRepository attemptRepository, StreakCalculator streakCalculator,
                         CacheService cacheService) {
        this.attemptRepository = attemptRepository;
        this.streakCalculator = streakCalculator;
        this.cacheService = cacheService;
    }

    @Transactional(readOnly = true)
    public StatsResponse getStats(UUID userId) {
        String cacheKey = "practice:stats:" + userId;
        StatsResponse cached = cacheService.get(cacheKey, StatsResponse.class);
        if (cached != null) {
            return cached;
        }

        long totalAttempts = attemptRepository.countByUserId(userId);
        Double totalDurationSeconds = attemptRepository.sumDuration(userId);
        double dictationAccuracy = orZero(attemptRepository.averageAccuracy(userId, "dictation"));
        double shadowingAccuracy = orZero(attemptRepository.averageAccuracy(userId, "shadowing"));
        double averageScore = orZero(attemptRepository.averageScore(userId));
        long lessonsCompleted = attemptRepository.countDistinctLessonsCompleted(userId);

        StreakCalculator.StreakResult streak = streakCalculator.calculate(attemptRepository.distinctPracticeDatesDesc(userId));

        List<PracticeAttempt> latest = attemptRepository.findTop1ByUserIdOrderByCreatedAtDesc(userId);
        OffsetDateTime lastPracticeDate = latest.isEmpty() ? null : latest.get(0).getCreatedAt();

        var trend = buildWeeklyTrend(userId);

        StatsResponse response = StatsResponse.builder()
                .totalAttempts(totalAttempts)
                .totalHoursLearned(round(orZero(totalDurationSeconds) / 3600.0))
                .dictationAccuracy(round(dictationAccuracy))
                .shadowingAccuracy(round(shadowingAccuracy))
                .currentStreak(streak.currentStreak())
                .longestStreak(streak.longestStreak())
                .lastPracticeDate(lastPracticeDate)
                .lessonsCompleted(lessonsCompleted)
                .averageScore(round(averageScore))
                .accuracy(trend)
                .build();

        cacheService.set(cacheKey, response, STATS_TTL);
        return response;
    }

    @Transactional(readOnly = true)
    public LessonStatsResponse getLessonStats(UUID userId, UUID lessonId) {
        List<PracticeAttempt> attempts = attemptRepository.findByUserIdAndLessonIdOrderByCreatedAtDesc(userId, lessonId);
        double avgAccuracy = attempts.stream()
                .filter(a -> a.getAccuracy() != null)
                .mapToDouble(a -> a.getAccuracy().doubleValue())
                .average().orElse(0.0);
        double bestScore = attempts.stream()
                .filter(a -> a.getScore() != null)
                .mapToDouble(a -> a.getScore().doubleValue())
                .max().orElse(0.0);
        OffsetDateTime lastAttempt = attempts.isEmpty() ? null : attempts.get(0).getCreatedAt();
        return LessonStatsResponse.builder()
                .attempts(attempts.size())
                .avgAccuracy(round(avgAccuracy))
                .bestScore(round(bestScore))
                .lastAttempt(lastAttempt)
                .build();
    }

    @Transactional(readOnly = true)
    public List<TrendingErrorResponse> getTrendingErrors(UUID userId) {
        List<PracticeAttempt> attempts = attemptRepository.search(userId, null, "dictation",
                        org.springframework.data.domain.PageRequest.of(0, 200)).getContent();

        Map<String, List<Double>> wordAccuracies = new HashMap<>();
        for (PracticeAttempt attempt : attempts) {
            if (attempt.getWordScores() == null) {
                continue;
            }
            attempt.getWordScores().forEach(node -> {
                boolean correct = node.path("isCorrect").asBoolean(true);
                String word = node.path("word").asText();
                if (!correct && !word.isBlank()) {
                    wordAccuracies.computeIfAbsent(word, w -> new ArrayList<>()).add(0.0);
                } else if (!word.isBlank()) {
                    wordAccuracies.computeIfAbsent(word, w -> new ArrayList<>()).add(100.0);
                }
            });
        }

        return wordAccuracies.entrySet().stream()
                .map(e -> {
                    double avg = e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    return new TrendingErrorResponse(e.getKey(), e.getValue().size(), round(avg));
                })
                .filter(t -> t.avgAccuracy() < 70)
                .sorted(Comparator.comparingLong(TrendingErrorResponse::count).reversed())
                .limit(20)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public StreakResponse getStreak(UUID userId) {
        StreakCalculator.StreakResult streak = streakCalculator.calculate(attemptRepository.distinctPracticeDatesDesc(userId));
        List<PracticeAttempt> latest = attemptRepository.findTop1ByUserIdOrderByCreatedAtDesc(userId);
        OffsetDateTime lastPracticeDate = latest.isEmpty() ? null : latest.get(0).getCreatedAt();
        return new StreakResponse(streak.currentStreak(), lastPracticeDate);
    }

    private StatsResponse.AccuracyTrend buildWeeklyTrend(UUID userId) {
        OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(6)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        List<Object[]> rows = attemptRepository.dailyAccuracySince(userId, since);

        Map<LocalDate, Double> byDate = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            Double avg = row[1] == null ? 0.0 : ((Number) row[1]).doubleValue();
            byDate.put(date, avg);
        }

        List<Double> weekly = new ArrayList<>();
        List<String> dates = new ArrayList<>();
        LocalDate cursor = since.toLocalDate();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        for (int i = 0; i < 7; i++) {
            LocalDate day = cursor.plusDays(i);
            weekly.add(round(byDate.getOrDefault(day, 0.0)));
            dates.add(day.format(formatter));
        }
        return new StatsResponse.AccuracyTrend(weekly, dates);
    }

    private double orZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
