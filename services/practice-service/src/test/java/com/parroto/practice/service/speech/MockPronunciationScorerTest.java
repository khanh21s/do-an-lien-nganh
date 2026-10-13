package com.parroto.practice.service.speech;

import com.parroto.practice.dto.ShadowingWordScoreDto;
import com.parroto.practice.exception.ApiException;
import com.parroto.practice.util.WavFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockPronunciationScorerTest {

    private static final String SENTENCE = "The quick brown fox jumps over the lazy dog.";
    private final MockPronunciationScorer scorer = new MockPronunciationScorer();

    @Test
    void sameInputGivesSameScores() {
        byte[] wav = WavFixtures.toneWav(3.0);
        var a = scorer.assess(wav, SENTENCE, "rec-1");
        var b = scorer.assess(wav.clone(), SENTENCE, "rec-1");
        assertThat(a).isEqualTo(b);
        assertThat(a.scorer()).isEqualTo("mock");
    }

    @Test
    void scoresEveryReferenceWordWithPhonemes() {
        var result = scorer.assess(WavFixtures.toneWav(3.0), SENTENCE, "rec-1");
        assertThat(result.wordScores()).extracting(ShadowingWordScoreDto::word)
                .containsExactly("the", "quick", "brown", "fox", "jumps", "over", "the", "lazy", "dog");
        for (ShadowingWordScoreDto w : result.wordScores()) {
            assertThat(w.wordAccuracy()).isBetween(0.0, 100.0);
            assertThat(w.phonemes()).isNotEmpty().hasSizeLessThanOrEqualTo(4);
            assertThat(w.phonemes()).allSatisfy(p ->
                    assertThat(Math.abs(p.accuracy() - w.wordAccuracy())).isLessThanOrEqualTo(5.0001));
            assertThat(w.status()).isEqualTo(PronunciationScorer.statusFor(w.wordAccuracy()));
        }
        double mean = result.wordScores().stream().mapToDouble(ShadowingWordScoreDto::wordAccuracy).average().orElseThrow();
        assertThat(result.overallAccuracy()).isCloseTo(mean, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void fullyVoicedRecordingScoresHigh() {
        var result = scorer.assess(WavFixtures.toneWav(3.0), SENTENCE, "rec-1");
        // voicedRatio = 1 -> base 95 +/- 7
        assertThat(result.overallAccuracy()).isGreaterThanOrEqualTo(80);
        assertThat(result.wordScores()).allSatisfy(w -> assertThat(w.wordAccuracy()).isBetween(88.0, 100.0));
    }

    @Test
    void silenceScoresBelow40() {
        var result = scorer.assess(WavFixtures.silentWav(3.0), SENTENCE, "rec-1");
        assertThat(result.overallAccuracy()).isLessThan(40);
        assertThat(result.wordScores()).allSatisfy(w -> {
            assertThat(w.wordAccuracy()).isBetween(20.0, 40.0);
            assertThat(w.status()).isEqualTo("red");
        });
    }

    @Test
    void partlyVoicedScoresBetweenSilenceAndFull() {
        double half = scorer.assess(WavFixtures.halfVoicedWav(4.0), SENTENCE, "rec-1").overallAccuracy();
        double full = scorer.assess(WavFixtures.toneWav(4.0), SENTENCE, "rec-1").overallAccuracy();
        double silent = scorer.assess(WavFixtures.silentWav(4.0), SENTENCE, "rec-1").overallAccuracy();
        assertThat(half).isLessThan(full).isGreaterThan(silent);
    }

    @Test
    void rejectsNonWav() {
        assertThatThrownBy(() -> scorer.assess("not a wav".getBytes(), SENTENCE, "x"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void emptyReferenceGivesZero() {
        var result = scorer.assess(WavFixtures.toneWav(1.0), "  ", "x");
        assertThat(result.overallAccuracy()).isZero();
        assertThat(result.wordScores()).isEmpty();
    }
}
