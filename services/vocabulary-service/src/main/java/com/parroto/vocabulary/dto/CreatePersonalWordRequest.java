package com.parroto.vocabulary.dto;

import com.parroto.common.exception.ApiException;

/**
 * Body of POST /vocabs/user/words and PUT /vocabs/user/words/{id} (T13).
 * <p>
 * Validated by {@link #normalized()} rather than Bean Validation: the contract requires 400
 * (the global Bean Validation handler answers 422) and limits apply to the trimmed values.
 */
public record CreatePersonalWordRequest(
        String word,
        String meaning,
        String ipa,
        String partOfSpeech,
        String example,
        String exampleVi
) {
    public static final int MAX_WORD = 100;
    public static final int MAX_MEANING = 500;
    public static final int MAX_IPA = 100;
    public static final int MAX_PART_OF_SPEECH = 50;
    public static final int MAX_EXAMPLE = 500;

    /** Trims every field, maps blank to null and enforces the limits; throws 400 on violation. */
    public CreatePersonalWordRequest normalized() {
        CreatePersonalWordRequest n = new CreatePersonalWordRequest(
                clean(word), clean(meaning), clean(ipa), clean(partOfSpeech), clean(example), clean(exampleVi));
        if (n.word() == null) throw ApiException.badRequest("Vui lòng nhập từ");
        if (n.meaning() == null) throw ApiException.badRequest("Vui lòng nhập nghĩa của từ");
        checkMax(n.word(), MAX_WORD, "Từ");
        checkMax(n.meaning(), MAX_MEANING, "Nghĩa");
        checkMax(n.ipa(), MAX_IPA, "Phiên âm");
        checkMax(n.partOfSpeech(), MAX_PART_OF_SPEECH, "Từ loại");
        checkMax(n.example(), MAX_EXAMPLE, "Câu ví dụ");
        checkMax(n.exampleVi(), MAX_EXAMPLE, "Bản dịch câu ví dụ");
        return n;
    }

    private static String clean(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static void checkMax(String value, int max, String label) {
        if (value != null && value.length() > max) {
            throw ApiException.badRequest("%s tối đa %d ký tự".formatted(label, max));
        }
    }
}
