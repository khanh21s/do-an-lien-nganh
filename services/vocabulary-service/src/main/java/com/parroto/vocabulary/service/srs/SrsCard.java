package com.parroto.vocabulary.service.srs;

import com.parroto.vocabulary.entity.CardState;
import com.parroto.vocabulary.entity.UserVocab;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Scheduling state of one card, independent of JPA so the scheduler stays a pure function.
 *
 * @param state        Anki state
 * @param step         index into the learning (LEARNING) or relearning (RELEARNING) steps; 0 otherwise
 * @param intervalDays REVIEW: current interval; RELEARNING: the interval the card gets back after
 *                     relearning; NEW/LEARNING: 0
 * @param ease         ease factor (2.50 = 250 %)
 * @param lapses       how often a review card was forgotten
 * @param due          LEARNING/RELEARNING: exact timestamp; REVIEW: 04:00 (Vietnam) of the due day
 */
public record SrsCard(CardState state, int step, int intervalDays, BigDecimal ease, int lapses, OffsetDateTime due) {

    public SrsCard {
        if (state == null) state = CardState.NEW;
        if (ease == null) ease = AnkiDefaults.STARTING_EASE;
        step = Math.max(0, step);
        intervalDays = Math.max(0, intervalDays);
        lapses = Math.max(0, lapses);
    }

    public static SrsCard newCard() {
        return new SrsCard(CardState.NEW, 0, 0, AnkiDefaults.STARTING_EASE, 0, null);
    }

    public static SrsCard of(UserVocab uv) {
        return new SrsCard(uv.getState(), nz(uv.getStep()), nz(uv.getIntervalDays()), uv.getEaseFactor(),
                nz(uv.getLapses()), uv.getNextReviewAt());
    }

    /** Copies the scheduling fields into the entity (the caller sets review counters and saves). */
    public void applyTo(UserVocab uv) {
        uv.setState(state);
        uv.setStep(step);
        uv.setIntervalDays(intervalDays);
        uv.setEaseFactor(ease);
        uv.setLapses(lapses);
        uv.setNextReviewAt(due);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
