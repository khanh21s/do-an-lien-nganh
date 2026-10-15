package com.parroto.vocabulary.service.dictionary;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * P2-C dictionary: normalises a clicked/typed word and proposes simple lemma fallbacks
 * (plural -s/-es/-ies, past -ed/-ied, -ing, possessive 's). Pure and deterministic: candidates
 * are tried in order, the word itself first; wrong guesses just miss.
 */
public final class WordNormalizer {

    private WordNormalizer() {
    }

    public static final int MAX_LENGTH = 64;
    private static final Pattern VALID = Pattern.compile("^\\p{L}[\\p{L}\\p{M}'\\- ]*$");
    private static final Pattern EDGE_JUNK = Pattern.compile("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$");
    private static final String VOWELS = "aeiou";

    /** Lower-cased, curly apostrophes straightened, surrounding punctuation removed, spaces collapsed. */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.replace('’', '\'').replace('‘', '\'').replace('ʼ', '\'')
                .replace(' ', ' ')
                .toLowerCase(Locale.ROOT)
                .trim();
        s = EDGE_JUNK.matcher(s).replaceAll("");
        return s.replaceAll("\\s+", " ");
    }

    public static boolean isValid(String normalized) {
        return normalized != null && !normalized.isEmpty() && normalized.length() <= MAX_LENGTH
                && VALID.matcher(normalized).matches();
    }

    /** The word, then lemma guesses (multi-word phrases: only the phrase itself). */
    public static List<String> candidates(String word) {
        Set<String> out = new LinkedHashSet<>();
        out.add(word);
        if (word.contains(" ")) return new ArrayList<>(out);
        String w = word;
        if (w.endsWith("'s") && w.length() > 3) {
            w = w.substring(0, w.length() - 2);
            out.add(w);
        } else if (w.endsWith("s'") && w.length() > 3) {
            w = w.substring(0, w.length() - 1);
            out.add(w);
        }
        int n = w.length();
        // plurals / 3rd person
        if (w.endsWith("ies") && n > 4) out.add(w.substring(0, n - 3) + "y");
        if (w.endsWith("es") && n > 3) {
            String stem = w.substring(0, n - 2);
            if (stem.endsWith("s") || stem.endsWith("x") || stem.endsWith("z") || stem.endsWith("ch")
                    || stem.endsWith("sh") || stem.endsWith("o")) {
                out.add(stem);
            }
        }
        if (w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is") && n > 3) {
            out.add(w.substring(0, n - 1));
        }
        // past tense / participle
        if (w.endsWith("ied") && n > 4) out.add(w.substring(0, n - 3) + "y");
        if (w.endsWith("ed") && n > 4) {
            String stem = w.substring(0, n - 2);
            out.add(stem);                       // walked -> walk
            out.add(stem + "e");                 // liked -> like (stem "lik")
            if (doubled(stem)) out.add(stem.substring(0, stem.length() - 1)); // stopped -> stop
        }
        // -ing
        if (w.endsWith("ing") && n > 5) {
            String stem = w.substring(0, n - 3);
            out.add(stem);                       // walking -> walk
            out.add(stem + "e");                 // making -> make
            if (doubled(stem)) out.add(stem.substring(0, stem.length() - 1)); // running -> run
        }
        if (w.endsWith("ying") && n == 5) out.add(w.substring(0, 1) + "ie"); // lying, dying, tying -> lie, die, tie
        out.removeIf(c -> c.length() < 2);
        return new ArrayList<>(out);
    }

    /** "stopp", "runn": the same consonant twice at the end. */
    private static boolean doubled(String stem) {
        int n = stem.length();
        if (n < 3) return false;
        char a = stem.charAt(n - 1);
        return a == stem.charAt(n - 2) && VOWELS.indexOf(a) < 0 && Character.isLetter(a) && a != 'l' && a != 's';
    }
}
