package com.parroto.practice.service.review;

/**
 * P2-A2 rules (decided by the user):
 * <ul>
 *   <li>a sentence enters the review queue when a Dictation attempt has accuracy &lt; 100 or a
 *       Shadowing attempt scores &lt; 70;</li>
 *   <li>an attempt made from the review screen is graded with SM-2 quality
 *       Dictation 100 → 4 (Good), ≥ 80 → 3 (Hard), else 1 (Again);
 *       Shadowing ≥ 85 → 4, ≥ 70 → 3, else 1.</li>
 * </ul>
 * {@code result} is the Dictation {@code accuracy} or the Shadowing {@code score} (both 0–100).
 */
public final class ReviewRules {

    public static final String DICTATION = "dictation";
    public static final String SHADOWING = "shadowing";

    public static final int QUALITY_AGAIN = 1;
    public static final int QUALITY_HARD = 3;
    public static final int QUALITY_GOOD = 4;

    static final double SHADOWING_PASS = 70.0;
    static final double SHADOWING_GOOD = 85.0;
    static final double DICTATION_PERFECT = 100.0;
    static final double DICTATION_HARD = 80.0;

    private ReviewRules() {
    }

    public static boolean fails(String mode, double result) {
        return switch (mode) {
            case DICTATION -> result < DICTATION_PERFECT;
            case SHADOWING -> result < SHADOWING_PASS;
            default -> throw new IllegalArgumentException("Unknown mode " + mode);
        };
    }

    public static int quality(String mode, double result) {
        return switch (mode) {
            case DICTATION -> result >= DICTATION_PERFECT ? QUALITY_GOOD
                    : result >= DICTATION_HARD ? QUALITY_HARD : QUALITY_AGAIN;
            case SHADOWING -> result >= SHADOWING_GOOD ? QUALITY_GOOD
                    : result >= SHADOWING_PASS ? QUALITY_HARD : QUALITY_AGAIN;
            default -> throw new IllegalArgumentException("Unknown mode " + mode);
        };
    }
}
