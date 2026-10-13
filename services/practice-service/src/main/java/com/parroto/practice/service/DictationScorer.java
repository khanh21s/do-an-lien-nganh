package com.parroto.practice.service;

import com.parroto.practice.dto.WordScoreDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Word-by-word dictation grading: normalizes both the reference transcript and the
 * user's typed input, then scores each expected word as correct if the corresponding
 * typed word matches exactly or is within a small Levenshtein distance (typo tolerance).
 */
@Component
public class DictationScorer {

    private static final Pattern PUNCTUATION = Pattern.compile("[\\p{Punct}]");
    private static final Pattern EXTRA_SPACES = Pattern.compile("\\s+");

    public record Result(List<WordScoreDto> wordScores, int totalWords, int correctWords, double accuracy) {
    }

    public Result score(String referenceText, String userInput) {
        List<String> expected = normalize(referenceText);
        List<String> typed = normalize(userInput);

        List<WordScoreDto> wordScores = new ArrayList<>();
        int correct = 0;
        for (int i = 0; i < expected.size(); i++) {
            String expectedWord = expected.get(i);
            String typedWord = i < typed.size() ? typed.get(i) : "";
            boolean isCorrect = isMatch(expectedWord, typedWord);
            if (isCorrect) {
                correct++;
            }
            wordScores.add(new WordScoreDto(i, expectedWord, isCorrect, isCorrect ? null : expectedWord));
        }
        int total = expected.size();
        double accuracy = total == 0 ? 0.0 : (correct * 100.0) / total;
        return new Result(wordScores, total, correct, accuracy);
    }

    /**
     * DIC-07: score = round2(accuracy x weight), weight Easy 0.6 / Normal 0.8 / Hard 1.0
     * (null or unknown difficulty -> Normal), clamped to [0, 100].
     */
    public double weightedScore(double accuracy, String difficulty) {
        double weight = weightFor(difficulty);
        double raw = accuracy * weight;
        if (Double.isNaN(raw)) {
            return 0.0;
        }
        double clamped = Math.max(0.0, Math.min(100.0, raw));
        return Math.round(clamped * 100.0) / 100.0;
    }

    public static double weightFor(String difficulty) {
        if (difficulty == null) {
            return 0.8;
        }
        return switch (difficulty) {
            case "Easy" -> 0.6;
            case "Hard" -> 1.0;
            default -> 0.8;
        };
    }

    private boolean isMatch(String expected, String typed) {
        if (expected.equals(typed)) {
            return true;
        }
        if (expected.isEmpty() || typed.isEmpty()) {
            return false;
        }
        // Allow small typos on longer words (roughly one edit per 4 characters).
        int allowedDistance = Math.max(1, expected.length() / 4);
        return levenshtein(expected, typed) <= allowedDistance;
    }

    /** Lower-cases, strips punctuation and splits on whitespace (shared with the mock pronunciation scorer). */
    public static List<String> normalize(String text) {
        if (text == null) {
            return List.of();
        }
        String cleaned = PUNCTUATION.matcher(text.toLowerCase().trim()).replaceAll(" ");
        cleaned = EXTRA_SPACES.matcher(cleaned).replaceAll(" ").trim();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        return List.of(cleaned.split(" "));
    }

    private int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }
}
