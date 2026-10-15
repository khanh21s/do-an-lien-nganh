package com.parroto.vocabulary.service;

import com.parroto.vocabulary.service.srs.Rating;

import com.parroto.vocabulary.entity.GameResult;

/**
 * Scoring of the "Dịch nhanh" game (T15), computed server-side:
 * <pre>
 * points = round(base(level) × combo multiplier × hint factor) + speed bonus
 * base         A1/A2 10, B1 15, B2 20, C1/C2 30 (no level: 15)
 * combo        after this answer: 5+ ×1.5, 10+ ×2, 20+ ×3 (a miss resets it to 0)
 * hint factor  0 hints ×1, 1 ×0.8, 2 ×0.5, 3 ×0.25
 * speed bonus  correct without hints: < 3 s +5, < 6 s +2
 * NEAR         half points at the current multiplier, combo kept (not increased), no bonus
 * </pre>
 */
public final class GameScoring {

    public static final int MAX_HINTS = 3;

    private GameScoring() {
    }

    public record Outcome(int points, int combo, double multiplier, int speedBonus) {
    }

    public static int basePoints(String level) {
        if (level == null) {
            return 15;
        }
        return switch (level.toUpperCase()) {
            case "A1", "A2" -> 10;
            case "B1" -> 15;
            case "B2" -> 20;
            case "C1", "C2" -> 30;
            default -> 15;
        };
    }

    public static double comboMultiplier(int combo) {
        if (combo >= 20) return 3.0;
        if (combo >= 10) return 2.0;
        if (combo >= 5) return 1.5;
        return 1.0;
    }

    public static double hintFactor(int hints) {
        return switch (Math.max(hints, 0)) {
            case 0 -> 1.0;
            case 1 -> 0.8;
            case 2 -> 0.5;
            default -> 0.25;
        };
    }

    public static int speedBonus(long elapsedMs) {
        if (elapsedMs < 3_000) return 5;
        if (elapsedMs < 6_000) return 2;
        return 0;
    }

    public static Outcome score(GameResult result, String level, int comboBefore, int hints, long elapsedMs) {
        int base = basePoints(level);
        return switch (result) {
            case CORRECT -> {
                int combo = comboBefore + 1;
                double mult = comboMultiplier(combo);
                int bonus = hints == 0 ? speedBonus(elapsedMs) : 0;
                yield new Outcome((int) Math.round(base * mult * hintFactor(hints)) + bonus, combo, mult, bonus);
            }
            case NEAR -> {
                double mult = comboMultiplier(comboBefore);
                yield new Outcome((int) Math.round(base * mult * hintFactor(hints) * 0.5), comboBefore, mult, 0);
            }
            case WRONG, SKIPPED -> new Outcome(0, 0, 1.0, 0);
        };
    }

    /** Answer button a game answer stands for: miss -> Again, hint or typo -> Hard, clean -> Good. */
    public static Rating srsRating(GameResult result, int hints) {
        if (result.isMiss()) return Rating.AGAIN;
        if (result == GameResult.NEAR || hints > 0) return Rating.HARD;
        return Rating.GOOD;
    }
}
