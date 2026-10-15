package com.parroto.vocabulary.service.importer;

import com.parroto.vocabulary.dto.ImportDtos.ImportRow;
import com.parroto.vocabulary.dto.ImportDtos.ImportRowInput;
import com.parroto.vocabulary.dto.CreatePersonalWordRequest;
import com.parroto.vocabulary.service.importer.VocabFileParser.RawEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * P2-C: cleans and validates import rows. Used for the preview and again for the commit (the
 * client sends the previewed rows back, so they are never trusted). Limits are those of a card
 * a learner adds by hand ({@link CreatePersonalWordRequest}).
 */
public final class ImportRowValidator {

    private ImportRowValidator() {
    }

    /** Cleaned values + errors of one row. {@code key} = lower(trim(word)) for duplicate checks. */
    public record Checked(String word, String meaning, String ipa, String partOfSpeech, String example,
                          String exampleVi, List<String> errors) {
        public boolean valid() {
            return errors.isEmpty();
        }

        public String key() {
            return word == null ? null : normalizeWord(word);
        }
    }

    public static Checked check(String word, String meaning, String ipa, String partOfSpeech, String example,
                                String exampleVi) {
        List<String> errors = new ArrayList<>();
        String w = clean(word);
        String m = clean(meaning);
        String i = cleanIpa(ipa);
        String p = clean(partOfSpeech);
        String e = clean(example);
        String ev = clean(exampleVi);
        if (w == null) errors.add("Thiếu từ");
        else if (!w.codePoints().anyMatch(Character::isLetter)) errors.add("Từ không hợp lệ");
        if (m == null) errors.add("Thiếu nghĩa");
        max(errors, w, CreatePersonalWordRequest.MAX_WORD, "Từ");
        max(errors, m, CreatePersonalWordRequest.MAX_MEANING, "Nghĩa");
        max(errors, i, CreatePersonalWordRequest.MAX_IPA, "Phiên âm");
        max(errors, p, CreatePersonalWordRequest.MAX_PART_OF_SPEECH, "Từ loại");
        max(errors, e, CreatePersonalWordRequest.MAX_EXAMPLE, "Câu ví dụ");
        max(errors, ev, CreatePersonalWordRequest.MAX_EXAMPLE, "Nghĩa câu ví dụ");
        return new Checked(w, m, i, p, e, ev, errors);
    }

    public static Checked check(ImportRowInput in) {
        return in == null ? check(null, null, null, null, null, null)
                : check(in.word(), in.meaning(), in.ipa(), in.partOfSpeech(), in.example(), in.exampleVi());
    }

    /**
     * Preview rows: values cleaned, errors per row, later repeats of a word flagged
     * {@code duplicateInFile}, words of {@code existingWords} (lower-cased) flagged {@code existsInDeck}.
     */
    public static List<ImportRow> toPreviewRows(List<RawEntry> entries, Collection<String> existingWords) {
        Set<String> existing = existingWords == null ? Set.of() : new HashSet<>(existingWords);
        Set<String> seen = new HashSet<>();
        List<ImportRow> rows = new ArrayList<>(entries.size());
        for (RawEntry raw : entries) {
            Checked c = check(raw.word(), raw.meaning(), raw.ipa(), raw.partOfSpeech(), raw.example(),
                    raw.exampleVi());
            List<String> errors = new ArrayList<>();
            if (raw.error() != null) errors.add(raw.error());
            else errors.addAll(c.errors());
            String key = c.key();
            boolean dupInFile = key != null && errors.isEmpty() && !seen.add(key);
            boolean inDeck = key != null && existing.contains(key);
            rows.add(new ImportRow(raw.line(), c.word(), c.meaning(), c.ipa(), c.partOfSpeech(), c.example(),
                    c.exampleVi(), List.copyOf(errors), dupInFile, inDeck));
        }
        return rows;
    }

    public static String normalizeWord(String word) {
        return word.trim().toLowerCase(Locale.ROOT);
    }

    static String clean(String s) {
        if (s == null) return null;
        String t = s.replace(' ', ' ').replaceAll("\\s+", " ").trim();
        return t.isEmpty() ? null : t;
    }

    /** "/təˈdeɪ/" or "[təˈdeɪ]" → "təˈdeɪ" (stored without delimiters, like hand-added words). */
    static String cleanIpa(String s) {
        String t = clean(s);
        if (t == null) return null;
        return clean(t.replaceAll("^[/\\[]+|[/\\]]+$", ""));
    }

    private static void max(List<String> errors, String value, int max, String label) {
        if (value != null && value.length() > max) {
            errors.add("%s dài quá %d ký tự".formatted(label, max));
        }
    }
}
