package com.parroto.practice.service.review;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P2-A2: queue rule + SM-2 quality mapping decided by the user. */
class ReviewRulesTest {

    @Test
    void dictationFailsBelowHundredPercent() {
        assertThat(ReviewRules.fails("dictation", 100.0)).isFalse();
        assertThat(ReviewRules.fails("dictation", 99.99)).isTrue();
        assertThat(ReviewRules.fails("dictation", 0.0)).isTrue();
    }

    @Test
    void shadowingFailsBelowSeventy() {
        assertThat(ReviewRules.fails("shadowing", 70.0)).isFalse();
        assertThat(ReviewRules.fails("shadowing", 69.99)).isTrue();
        assertThat(ReviewRules.fails("shadowing", 95.0)).isFalse();
    }

    @Test
    void dictationQuality() {
        assertThat(ReviewRules.quality("dictation", 100.0)).isEqualTo(4);
        assertThat(ReviewRules.quality("dictation", 99.0)).isEqualTo(3);
        assertThat(ReviewRules.quality("dictation", 80.0)).isEqualTo(3);
        assertThat(ReviewRules.quality("dictation", 79.99)).isEqualTo(1);
        assertThat(ReviewRules.quality("dictation", 0.0)).isEqualTo(1);
    }

    @Test
    void shadowingQuality() {
        assertThat(ReviewRules.quality("shadowing", 100.0)).isEqualTo(4);
        assertThat(ReviewRules.quality("shadowing", 85.0)).isEqualTo(4);
        assertThat(ReviewRules.quality("shadowing", 84.99)).isEqualTo(3);
        assertThat(ReviewRules.quality("shadowing", 70.0)).isEqualTo(3);
        assertThat(ReviewRules.quality("shadowing", 69.99)).isEqualTo(1);
    }

    @Test
    void unknownModeIsRejected() {
        assertThatThrownBy(() -> ReviewRules.fails("karaoke", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReviewRules.quality("karaoke", 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
