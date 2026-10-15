package com.parroto.vocabulary.service.dictionary;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WordNormalizerTest {

    @Test
    void normalizeStripsPunctuationAndCase() {
        assertThat(WordNormalizer.normalize("  \"Hello,")).isEqualTo("hello");
        assertThat(WordNormalizer.normalize("Don’t!")).isEqualTo("don't");
        assertThat(WordNormalizer.normalize("(well-known)")).isEqualTo("well-known");
        assertThat(WordNormalizer.normalize("Take   OFF.")).isEqualTo("take off");
        assertThat(WordNormalizer.normalize("...")).isEmpty();
    }

    @Test
    void validity() {
        assertThat(WordNormalizer.isValid("café")).isTrue();
        assertThat(WordNormalizer.isValid("don't")).isTrue();
        assertThat(WordNormalizer.isValid("")).isFalse();
        assertThat(WordNormalizer.isValid("abc123")).isFalse();
        assertThat(WordNormalizer.isValid("<script>")).isFalse();
        assertThat(WordNormalizer.isValid("a".repeat(65))).isFalse();
    }

    @Test
    void lemmaFallbacksKeepTheWordFirst() {
        assertThat(WordNormalizer.candidates("cats")).containsExactly("cats", "cat");
        assertThat(WordNormalizer.candidates("boxes")).startsWith("boxes").contains("box");
        assertThat(WordNormalizer.candidates("cities")).startsWith("cities").contains("city");
        assertThat(WordNormalizer.candidates("studied")).contains("study");
        assertThat(WordNormalizer.candidates("walked")).contains("walk");
        assertThat(WordNormalizer.candidates("liked")).contains("like");
        assertThat(WordNormalizer.candidates("stopped")).contains("stop");
        assertThat(WordNormalizer.candidates("walking")).contains("walk");
        assertThat(WordNormalizer.candidates("making")).contains("make");
        assertThat(WordNormalizer.candidates("running")).contains("run");
        assertThat(WordNormalizer.candidates("lying")).contains("lie");
        assertThat(WordNormalizer.candidates("john's")).containsExactly("john's", "john");
    }

    @Test
    void noBogusFallbacks() {
        assertThat(WordNormalizer.candidates("glass")).containsExactly("glass");
        assertThat(WordNormalizer.candidates("bus")).containsExactly("bus");
        assertThat(WordNormalizer.candidates("this")).containsExactly("this");
        assertThat(WordNormalizer.candidates("sing")).containsExactly("sing");
        assertThat(WordNormalizer.candidates("red")).containsExactly("red");
        assertThat(WordNormalizer.candidates("take off")).containsExactly("take off");
        assertThat(WordNormalizer.candidates("falling")).contains("fall").doesNotContain("fal");
    }
}
