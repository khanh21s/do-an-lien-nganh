package com.parroto.vocabulary.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** P2-anki study session payloads (deck study and the global "Ôn tập" page). */
public final class StudyDtos {

    private StudyDtos() {
    }

    /** Anki counters: new (blue), learning (red), review (green). No daily limits. */
    public record StudyCounts(@JsonProperty("new") long newCount, long learning, long review) {
        public long total() {
            return newCount + learning + review;
        }
    }

    /** What one button will do, computed by the server's scheduler. */
    public record RatingPreview(int rating, String label, String state, int intervalDays, OffsetDateTime nextReviewAt) {
    }

    @Builder
    public record StudyCard(
            /** null for a deck card that was never graded (the grade request then sends cardId). */
            UUID userVocabId,
            UUID cardId,
            UUID deckId,
            UUID groupId,
            String word,
            String meaning,
            String example,
            String exampleVi,
            String ipa,
            String partOfSpeech,
            String audioUrl,
            String imageUrl,
            /** NEW, LEARNING, REVIEW or RELEARNING. */
            String state,
            /** Which counter the card belongs to: new, learning or review. */
            String queue,
            int intervalDays,
            double easeFactor,
            int lapses,
            int repetitions,
            OffsetDateTime nextReviewAt,
            /** Again, Hard, Good, Easy. */
            List<RatingPreview> previews
    ) {
    }

    /**
     * The session state: counters, the card to show now (null when the user is done for today)
     * and, when done, the time the next learning/review card falls due.
     */
    public record StudySession(StudyCounts counts, StudyCard card, OffsetDateTime nextDueAt, OffsetDateTime now) {
    }
}
