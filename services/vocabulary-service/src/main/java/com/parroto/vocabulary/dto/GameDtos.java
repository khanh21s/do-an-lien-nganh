package com.parroto.vocabulary.dto;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Request/response bodies of the "Dịch nhanh" game API (T15, /vocabs/games). */
public final class GameDtos {

    private GameDtos() {
    }

    /** {@code source}: DECK (needs deckId) | NOTEBOOK | LEVEL (needs level A1–C2). */
    public record StartGameRequest(String mode, String source, UUID deckId, String level) {
    }

    public record AnswerRequest(String answer, Boolean skip) {
    }

    /** The prompt shown to the player; the English word itself never leaves the server until answered. */
    public record Question(int seq, String meaning, String level, int basePoints, boolean retry) {
    }

    @Builder
    public record GameState(
            UUID sessionId,
            String mode,
            String source,
            String sourceLabel,
            int durationSec,
            long remainingMs,
            int score,
            int combo,
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
            String correctWord,
            /** Set when another word with the same meaning was accepted. */
            String acceptedWord,
            String ipa,
            String partOfSpeech,
            String example,
            String exampleVi,
            String audioUrl,
            /** The answer arrived after the time was up: not graded. */
            boolean timeUp,
            /** No next question (time up or no words left): call finish. */
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
            List<SummaryItem> items
    ) {
    }

    public record Best(String mode, String sourceKey, String sourceLabel, int score, int bestCombo, OffsetDateTime playedAt) {
    }

    public record Stats(long gamesPlayed, long totalScore, List<Best> bests) {
    }
}
