package com.parroto.vocabulary.service.deck;

import com.parroto.vocabulary.service.PersonalWordService;

/**
 * P2-C: every limit of personal decks, groups and import in one place.
 * <p>
 * Deck/card limits cover the user's own decks <em>other than</em> the notebook; the notebook keeps
 * its existing cap ({@link PersonalWordService#MAX_PERSONAL_WORDS}) so nothing changes for it.
 * Public (admin) decks have no deck/card limit, only the per-import limits.
 */
public final class VocabLimits {

    private VocabLimits() {
    }

    public static final int FREE_MAX_DECKS = 5;
    public static final int FREE_MAX_CARDS = 500;
    public static final int PREMIUM_MAX_DECKS = 50;
    public static final int PREMIUM_MAX_CARDS = 8_000;

    public static final int NOTEBOOK_MAX_CARDS = PersonalWordService.MAX_PERSONAL_WORDS;

    public static final int MAX_GROUPS_PER_DECK = 100;
    public static final int GROUP_NAME_MAX = 100;
    public static final int DECK_NAME_MAX = 100;
    public static final int DECK_DESCRIPTION_MAX = 500;

    public static final long IMPORT_MAX_BYTES = 2L * 1024 * 1024;
    public static final int IMPORT_MAX_ROWS = 2_000;

    public static int maxDecks(boolean premium) {
        return premium ? PREMIUM_MAX_DECKS : FREE_MAX_DECKS;
    }

    public static int maxCards(boolean premium) {
        return premium ? PREMIUM_MAX_CARDS : FREE_MAX_CARDS;
    }

    /** "1.000" style grouping used in the Vietnamese messages. */
    public static String vn(long n) {
        return String.format(java.util.Locale.ROOT, "%,d", n).replace(',', '.');
    }

    public static String deckLimitMessage(boolean premium) {
        return premium
                ? "Bạn đã đạt giới hạn %d bộ thẻ cá nhân".formatted(PREMIUM_MAX_DECKS)
                : "Tài khoản miễn phí tạo tối đa %d bộ thẻ cá nhân – nâng cấp Premium để tạo tới %d bộ"
                        .formatted(FREE_MAX_DECKS, PREMIUM_MAX_DECKS);
    }

    /** {@code remaining} = how many more cards still fit (may be 0). */
    public static String cardLimitMessage(boolean premium, long remaining) {
        String base = premium
                ? "Bộ thẻ cá nhân tối đa %s thẻ".formatted(vn(PREMIUM_MAX_CARDS))
                : "Tài khoản miễn phí lưu tối đa %s thẻ trong các bộ thẻ cá nhân (Premium: %s)"
                        .formatted(vn(FREE_MAX_CARDS), vn(PREMIUM_MAX_CARDS));
        return base + " – còn trống %s thẻ".formatted(vn(Math.max(0, remaining)));
    }
}
