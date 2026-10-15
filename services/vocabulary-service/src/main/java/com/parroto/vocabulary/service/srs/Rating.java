package com.parroto.vocabulary.service.srs;

import com.parroto.common.exception.ApiException;

/** The four answer buttons, numbered like Anki (and like the keyboard keys 1–4). */
public enum Rating {
    AGAIN(1, 1),
    HARD(2, 3),
    GOOD(3, 4),
    EASY(4, 5);

    /** Anki button number, sent by the client as {@code rating}. */
    public final int value;
    /**
     * SM-2 quality stored in {@code user_vocab.quality} (unchanged meaning since Phase 1:
     * Again=1, Hard=3, Good=4, Easy=5), so the notebook accuracy (quality ≥ 3) keeps working.
     */
    public final int legacyQuality;

    Rating(int value, int legacyQuality) {
        this.value = value;
        this.legacyQuality = legacyQuality;
    }

    public static Rating of(int value) {
        for (Rating r : values()) {
            if (r.value == value) return r;
        }
        throw ApiException.badRequest("rating phải từ 1 đến 4");
    }

    /** Phase-1 clients sent an SM-2 quality 0–5: 0–2 = Again, 3 = Hard, 4 = Good, 5 = Easy. */
    public static Rating fromLegacyQuality(int quality) {
        if (quality < 0 || quality > 5) throw ApiException.badRequest("quality phải từ 0 đến 5");
        if (quality <= 2) return AGAIN;
        if (quality == 3) return HARD;
        if (quality == 4) return GOOD;
        return EASY;
    }
}
