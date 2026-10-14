package com.parroto.vocabulary.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Request/response bodies of the "Dịch nhanh" game API (T15/T16, /vocabs/games). */
public final class GameDtos {

    private GameDtos() {
    }

    /**
     * {@code mode}: SPRINT (default) | SURVIVAL | DAILY. {@code source} only for SPRINT:
     * DECK (needs deckId) | NOTEBOOK | LEVEL (needs level A1–C2).
     */
    public record StartGameRequest(String mode, String source, UUID deckId, String level) {
    }

    public record AnswerRequest(String answer, Boolean skip) {
    }

    public record GoalRequest(Integer dailyGoal) {
    }

    /**
     * The prompt shown to the player; the English word itself never leaves the server until answered.
     * {@code timeLeftMs}: time left for this word (SURVIVAL/DAILY), null in SPRINT.
     */
    public record Question(int seq, String meaning, String level, int basePoints, boolean retry, Long timeLeftMs) {
    }

    @Builder
    public record GameState(
            UUID sessionId,
            String mode,
            String source,
            String sourceLabel,
            /** Round timer in seconds (SPRINT), 0 when only the per-word timer counts. */
            int durationSec,
            long remainingMs,
            int score,
            int combo,
            /** Lives left (SURVIVAL), else null. */
            Integer lives,
            /** Number of words of the round (DAILY), else null. */
            Integer questionCount,
            Integer previousBest,
            Question question
    ) {
    }

    @Builder
    public record Hint(int hintsUsed, int maxHints, String partOfSpeech, int letterCount, String mask, String example) {
    }

    @Builder
    public record AnswerResult(
            String result,
            int points,
            int speedBonus,
            double multiplier,
            int combo,
            int score,
            Integer lives,
            String correctWord,
            /** Set when another word with the same meaning was accepted. */
            String acceptedWord,
            String ipa,
            String partOfSpeech,
            String example,
            String exampleVi,
            String audioUrl,
            /** The answer came after the word's time limit: graded WRONG. */
            boolean timedOut,
            /** The answer arrived after the round was over: not graded. */
            boolean timeUp,
            /** No next question (time up, no lives, last word, no words left): call finish. */
            boolean finished,
            long remainingMs,
            Question nextQuestion
    ) {
    }

    @Builder
    public record SummaryItem(
            int seq,
            UUID cardId,
            String word,
            String meaning,
            String ipa,
            String partOfSpeech,
            String example,
            String exampleVi,
            String audioUrl,
            String result,
            String answer,
            int hintsUsed,
            int points,
            boolean retry,
            /** Notebook entry of this word, null when it is not in the notebook. */
            UUID userVocabId
    ) {
    }

    public record Badge(String code, String icon, String title, String description, OffsetDateTime earnedAt) {
    }

    /** XP / level / daily goal of the player. */
    @Builder
    public record Progress(
            long xp,
            int level,
            /** XP earned inside the current level and needed to reach the next one. */
            long levelXp,
            long levelXpNeeded,
            int dailyGoal,
            long todayPoints,
            boolean goalMet
    ) {
    }

    @Builder
    public record Summary(
            UUID sessionId,
            String mode,
            String source,
            String sourceLabel,
            int score,
            int correct,
            int near,
            int wrong,
            int skipped,
            int bestCombo,
            /** (correct + near) / answered, 0–100. */
            int accuracy,
            Integer previousBest,
            boolean newRecord,
            int srsUpdated,
            OffsetDateTime finishedAt,
            List<SummaryItem> items,
            // --- T16 ---
            int xpGained,
            boolean leveledUp,
            Progress progress,
            /** The daily goal was reached by this round. */
            boolean goalJustMet,
            List<Badge> newBadges,
            /** Leaderboard of this round (daily / sprint-week / survival-week), null when unranked. */
            String board,
            Integer rank,
            Integer totalPlayers
    ) {
    }

    public record Best(String mode, String sourceKey, String sourceLabel, int score, int bestCombo, OffsetDateTime playedAt) {
    }

    @Builder
    public record DailyStatus(LocalDate date, boolean played, boolean active, Integer score, Integer rank, Integer totalPlayers) {
    }

    @Builder
    public record Stats(
            long gamesPlayed,
            long totalScore,
            List<Best> bests,
            Progress progress,
            /** Every badge of the catalogue; {@code earnedAt} null when not earned yet. */
            List<Badge> badges,
            DailyStatus daily
    ) {
    }

    public record LeaderboardEntry(int rank, String displayName, int score, int bestCombo, boolean me) {
    }

    @Builder
    public record Leaderboard(
            String board,
            String title,
            LocalDate from,
            LocalDate to,
            List<LeaderboardEntry> entries,
            /** The caller's row (also when outside the top list), null when not ranked yet. */
            LeaderboardEntry me,
            int totalPlayers
    ) {
    }
}
