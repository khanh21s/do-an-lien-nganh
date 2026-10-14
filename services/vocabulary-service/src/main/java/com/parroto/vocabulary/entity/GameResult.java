package com.parroto.vocabulary.entity;

/** Outcome of one game question (T15). */
public enum GameResult {
    /** The card's word, or another word with the same meaning. */
    CORRECT,
    /** One typo away (insert/delete/replace/swap) from the word: half points, combo kept. */
    NEAR,
    WRONG,
    SKIPPED;

    public boolean isMiss() {
        return this == WRONG || this == SKIPPED;
    }
}
