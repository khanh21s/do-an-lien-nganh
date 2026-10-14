package com.parroto.vocabulary.service;

import com.parroto.vocabulary.entity.GameResult;
import com.parroto.vocabulary.entity.VocabCard;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Grades a typed English answer against a card (T15 "Dịch nhanh").
 * <ul>
 *   <li>Case, extra spaces, a trailing "." / "!" / "?", text in parentheses and a leading "to "
 *       are ignored; "colour/color" accepts either spelling.</li>
 *   <li>Another word whose Vietnamese meaning shares a segment with the question's meaning
 *       (segments split on , ; /) is CORRECT too, e.g. "huge" for "to lớn, khổng lồ".</li>
 *   <li>One typo away (insert/delete/replace or two swapped letters) from a word of 4+ letters
 *       is NEAR.</li>
 * </ul>
 */
public final class GameAnswerChecker {

    /** Words shorter than this need an exact answer (a 1-letter typo of "cat" is another word). */
    static final int NEAR_MIN_LENGTH = 4;
    private static final Pattern PARENS = Pattern.compile("\\([^)]*\\)");
    private static final Pattern ALTERNATIVES = Pattern.compile("[/;,]");

    private GameAnswerChecker() {
    }

    public record Check(GameResult result, String acceptedWord) {
        static Check of(GameResult result) {
            return new Check(result, null);
        }
    }

    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String t = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replace('’', '\'').replace('‘', '\'');
        t = PARENS.matcher(t).replaceAll(" ");
        t = t.replaceAll("\\s+", " ").trim();
        t = t.replaceAll("[.!?…]+$", "").trim();
        if (t.startsWith("to ") && t.length() > 3) {
            t = t.substring(3).trim();
        }
        return t;
    }

    /** Accepted spellings of a card's word, e.g. "colour/color" -> [colour, color]. */
    public static List<String> forms(String word) {
        Set<String> out = new LinkedHashSet<>();
        for (String part : ALTERNATIVES.split(word == null ? "" : word)) {
            String n = normalize(part);
            if (!n.isEmpty()) {
                out.add(n);
            }
        }
        if (out.isEmpty()) {
            out.add(normalize(word));
        }
        return List.copyOf(out);
    }

    static Set<String> meaningSegments(String meaning) {
        if (meaning == null) {
            return Set.of();
        }
        return Arrays.stream(ALTERNATIVES.split(PARENS.matcher(meaning.toLowerCase(Locale.ROOT)).replaceAll(" ")))
                .map(p -> Normalizer.normalize(p, Normalizer.Form.NFC).replaceAll("\\s+", " ").trim())
                .filter(p -> !p.isEmpty())
                .collect(Collectors.toSet());
    }

    static boolean sameMeaning(String a, String b) {
        Set<String> sa = meaningSegments(a);
        return meaningSegments(b).stream().anyMatch(sa::contains);
    }

    /** Lower-cased spellings to look up as other cards' words (see {@code findSameWordVisibleTo}). */
    public static Set<String> lookupKeys(String answer) {
        String a = normalize(answer);
        return a.isEmpty() ? Set.of() : Set.of(a, "to " + a);
    }

    /**
     * @param sameWordCards cards spelled like the answer (any deck the user can see); used for
     *                      the "same meaning, other word" rule. May include {@code question}.
     */
    public static Check check(String answer, VocabCard question, Collection<VocabCard> sameWordCards) {
        String a = normalize(answer);
        if (a.isEmpty()) {
            return Check.of(GameResult.WRONG);
        }
        List<String> forms = forms(question.getWord());
        if (forms.contains(a)) {
            return Check.of(GameResult.CORRECT);
        }
        for (VocabCard other : sameWordCards) {
            if (other.getId() != null && other.getId().equals(question.getId())) {
                continue;
            }
            if (forms(other.getWord()).contains(a) && sameMeaning(other.getMeaning(), question.getMeaning())) {
                return new Check(GameResult.CORRECT, other.getWord());
            }
        }
        for (String form : forms) {
            if (form.length() >= NEAR_MIN_LENGTH && distance(a, form) == 1) {
                return Check.of(GameResult.NEAR);
            }
        }
        return Check.of(GameResult.WRONG);
    }

    /** Optimal string alignment distance (Levenshtein + adjacent transposition). */
    static int distance(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (Math.abs(n - m) > 1) {
            return Math.abs(n - m);
        }
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[n][m];
    }

    // --- hints ---

    /**
     * Letter mask of the word: level 1 "_ _ _ _", level 2+ reveals the first letter of each word
     * ("h _ _ _"); {@code revealLast} also shows the last letter (level 3 without an example).
     */
    public static String mask(String word, int level, boolean revealLast) {
        String w = displayForm(word);
        StringBuilder out = new StringBuilder();
        boolean wordStart = true;
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                out.append(c == ' ' ? "   " : c + " ");
                wordStart = true;
                continue;
            }
            boolean last = i == w.length() - 1;
            boolean show = (level >= 2 && wordStart) || (revealLast && last);
            out.append(show ? c : '_').append(' ');
            wordStart = false;
        }
        return out.toString().trim();
    }

    public static int letterCount(String word) {
        return (int) displayForm(word).chars().filter(Character::isLetterOrDigit).count();
    }

    /** The example sentence with the word (and its inflections: decide -> decided) blanked out, or null. */
    public static String blankedExample(String example, String word) {
        if (example == null || example.isBlank()) {
            return null;
        }
        String w = displayForm(word);
        if (w.isEmpty()) {
            return null;
        }
        String stem = w.length() > 4 ? w.substring(0, w.length() - 1) : w;
        Matcher m = Pattern.compile("(?i)\\b" + Pattern.quote(stem) + "[a-z]*").matcher(example);
        if (!m.find()) {
            return null;
        }
        return m.replaceAll("_____");
    }

    private static String displayForm(String word) {
        return forms(word).get(0);
    }
}
