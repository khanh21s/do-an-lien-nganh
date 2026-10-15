package com.parroto.vocabulary.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** P2-B request/response bodies of /vocabs/gamification and /internal/gamification. */
public final class GamificationDtos {

    private GamificationDtos() {
    }

    // ------------------------------------------------------------------ internal events

    /**
     * Sent by practice-service after a dictation/shadowing attempt is saved.
     * {@code type}: DICTATION | SHADOWING. {@code accuracy}/{@code score}: 0–100 as stored on the attempt.
     * {@code occurredAt}: attempt time (decides the Vietnam day), defaults to now.
     */
    public record PracticeEventRequest(UUID userId, String type, UUID attemptId, UUID lessonId, UUID sentenceId,
                                       Double accuracy, Double score, OffsetDateTime occurredAt) {
    }

    /** {@code granted} false: the award was already given (duplicate event or anti-farming limit). */
    public record AwardResult(String source, int amount, boolean granted) {
    }

    public record EventResult(int xpGained, LocalDate day, List<AwardResult> awards) {
    }

    // ------------------------------------------------------------------ me

    @Builder
    public record CheckInState(
            boolean checkedInToday,
            /** Current check-in streak (0 when broken). */
            int streak,
            LocalDate lastCheckInDay,
            /** Diamonds of today's check-in: received (checked in) or on offer (not yet). */
            int todayReward,
            /** Days left until the next 7-day bonus. */
            int daysToBonus
    ) {
    }

    @Builder
    public record MissionState(String code, String icon, String title, int target, int progress,
                               boolean completed, int reward) {
    }

    @Builder
    public record GamificationMe(
            long xp,
            int level,
            long levelXp,
            long levelXpNeeded,
            long todayXp,
            long weekXp,
            long diamonds,
            LocalDate today,
            CheckInState checkIn,
            List<MissionState> missions
    ) {
    }

    @Builder
    public record CheckInResult(
            boolean alreadyCheckedIn,
            int diamondsGained,
            /** Part of {@code diamondsGained} that is the 7-day streak bonus. */
            int bonusDiamonds,
            int streak,
            long diamonds
    ) {
    }

    // ------------------------------------------------------------------ leaderboard

    public record XpLeaderboardEntry(int rank, String displayName, long xp, boolean me) {
    }

    @Builder
    public record XpLeaderboard(
            String period,
            String title,
            LocalDate from,
            LocalDate to,
            List<XpLeaderboardEntry> entries,
            /** The caller's row (also when outside the top list), null without XP in the period. */
            XpLeaderboardEntry me,
            int totalPlayers
    ) {
    }
}
