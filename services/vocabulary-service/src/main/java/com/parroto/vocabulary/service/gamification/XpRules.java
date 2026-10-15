package com.parroto.vocabulary.service.gamification;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * P2-B: every gamification number in one place (documented in docs/reports/P2-B.md).
 *
 * <pre>
 * XP (shared by all activities)
 *   Dictation sentence   +5, +5 more when accuracy = 100 %      once per sentence per day
 *   Shadowing sentence   +5, +5 more when score ≥ 85             once per sentence per day
 *   SRS card review      +2                                       once per card per day
 *   "Dịch nhanh" round   its score                                once per round
 * Level: level L → L+1 needs 100·L XP (level L starts at 50·L·(L−1) XP).
 * Diamonds ("kim cương")
 *   daily check-in       +50, +100 more on every 7th day of a check-in streak (7, 14, 21 …)
 *   daily mission        +30 each, granted automatically when completed (3 missions per day)
 * Days are calendar days in Vietnam time; weeks run Monday–Sunday.
 * </pre>
 */
public final class XpRules {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public static final int DICTATION_XP = 5;
    public static final int DICTATION_PERFECT_BONUS_XP = 5;
    public static final double DICTATION_PERFECT_ACCURACY = 100.0;
    public static final int SHADOWING_XP = 5;
    public static final int SHADOWING_GOOD_BONUS_XP = 5;
    public static final double SHADOWING_GOOD_SCORE = 85.0;
    public static final int CARD_REVIEW_XP = 2;

    public static final int CHECKIN_DIAMONDS = 50;
    public static final int CHECKIN_STREAK_BONUS_EVERY = 7;
    public static final int CHECKIN_STREAK_BONUS_DIAMONDS = 100;
    public static final int MISSION_DIAMONDS = 30;
    public static final int MISSIONS_PER_DAY = 3;

    public static final int LEADERBOARD_SIZE = 20;

    /** Accuracy is stored with 2 decimals; anything that rounds to 100.00 is a perfect sentence. */
    private static final double EPSILON = 0.005;

    private XpRules() {
    }

    /** Ledger source of an XP row ({@code xp_event.source}). */
    public enum XpSource {
        DICTATION, DICTATION_PERFECT, SHADOWING, SHADOWING_GOOD, CARD_REVIEW, GAME,
        /** Migration V2: game XP that was not backed by a finished round row. */
        GAME_LEGACY
    }

    /** Ledger source of a diamond row ({@code diamond_event.source}). */
    public enum DiamondSource {
        CHECKIN, CHECKIN_STREAK_BONUS, MISSION
    }

    /** Practice activity reported by practice-service. */
    public enum PracticeType {
        DICTATION, SHADOWING
    }

    /** One XP row to write; {@code refId} is the idempotency / anti-farming key. */
    public record Award(XpSource source, String refId, int amount) {
    }

    // ------------------------------------------------------------------ XP awards

    /**
     * Awards of one dictation/shadowing attempt. The key is user + sentence + day, so the same
     * sentence gives its base XP once per day per mode and its bonus once per day per mode; a
     * duplicate delivery of the same attempt maps to the same keys and is ignored.
     */
    public static List<Award> forPractice(PracticeType type, UUID userId, UUID sentenceId, LocalDate day,
                                          Double accuracy, Double score) {
        String key = userId + ":" + sentenceId + ":" + day;
        List<Award> awards = new ArrayList<>(2);
        switch (type) {
            case DICTATION -> {
                awards.add(new Award(XpSource.DICTATION, key, DICTATION_XP));
                if (accuracy != null && accuracy >= DICTATION_PERFECT_ACCURACY - EPSILON) {
                    awards.add(new Award(XpSource.DICTATION_PERFECT, key, DICTATION_PERFECT_BONUS_XP));
                }
            }
            case SHADOWING -> {
                awards.add(new Award(XpSource.SHADOWING, key, SHADOWING_XP));
                double s = score != null ? score : accuracy != null ? accuracy : 0;
                if (s >= SHADOWING_GOOD_SCORE - EPSILON) {
                    awards.add(new Award(XpSource.SHADOWING_GOOD, key, SHADOWING_GOOD_BONUS_XP));
                }
            }
        }
        return awards;
    }

    /** One card (notebook entry) gives review XP once per day. */
    public static Award forCardReview(UUID userId, UUID userVocabId, LocalDate day) {
        return new Award(XpSource.CARD_REVIEW, userId + ":" + userVocabId + ":" + day, CARD_REVIEW_XP);
    }

    /** A finished game round gives its score once (same key as the V2 backfill). */
    public static Award forGameRound(UUID sessionId, int score) {
        return new Award(XpSource.GAME, sessionId.toString(), Math.max(score, 0));
    }

    // ------------------------------------------------------------------ levels

    /** XP at which {@code level} starts: 0, 100, 300, 600, 1000 … */
    public static long levelStart(int level) {
        return 50L * level * (level - 1);
    }

    public static int levelFor(long xp) {
        int level = 1;
        while (levelStart(level + 1) <= Math.max(xp, 0)) {
            level++;
        }
        return level;
    }

    // ------------------------------------------------------------------ check-in

    /** Streak after checking in on {@code today}: +1 after a check-in yesterday, else a new streak of 1. */
    public static int nextCheckInStreak(LocalDate lastCheckIn, int currentStreak, LocalDate today) {
        if (lastCheckIn != null && lastCheckIn.plusDays(1).equals(today)) {
            return Math.max(currentStreak, 0) + 1;
        }
        return 1;
    }

    /** Streak still alive on {@code today} (checked in today or yesterday), else 0. */
    public static int liveCheckInStreak(LocalDate lastCheckIn, int storedStreak, LocalDate today) {
        if (lastCheckIn != null && (lastCheckIn.equals(today) || lastCheckIn.plusDays(1).equals(today))) {
            return storedStreak;
        }
        return 0;
    }

    public static int checkInBonus(int streak) {
        return streak > 0 && streak % CHECKIN_STREAK_BONUS_EVERY == 0 ? CHECKIN_STREAK_BONUS_DIAMONDS : 0;
    }

    // ------------------------------------------------------------------ time

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }

    public static LocalDate dayOf(OffsetDateTime instant) {
        return instant.atZoneSameInstant(ZONE).toLocalDate();
    }

    public static LocalDate weekStart(LocalDate day) {
        return day.with(DayOfWeek.MONDAY);
    }
}
