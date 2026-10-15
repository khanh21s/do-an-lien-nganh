package com.parroto.vocabulary.entity;

/** Anki card states of a {@link UserVocab} row ({@code user_vocab.state}, V4). */
public enum CardState {
    /** In the user's collection but never graded (notebook word, "add to notebook"). */
    NEW,
    /** Going through the learning steps (1m, 10m). */
    LEARNING,
    /** Graduated: day-based intervals. */
    REVIEW,
    /** Lapsed review card going through the relearning steps (10m). */
    RELEARNING;

    public boolean isLearning() {
        return this == LEARNING || this == RELEARNING;
    }
}
