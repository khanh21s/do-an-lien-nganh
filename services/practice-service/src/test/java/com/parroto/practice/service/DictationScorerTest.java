package com.parroto.practice.service;

import com.parroto.practice.dto.WordScoreDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DictationScorerTest {

    private final DictationScorer scorer = new DictationScorer();

    @Test
    void perfectAnswerScoresByDifficultyWeight() {
        DictationScorer.Result result = scorer.score("Hello, world! How are you?", "hello world how are you");
        assertThat(result.accuracy()).isEqualTo(100.0);
        assertThat(scorer.weightedScore(result.accuracy(), "Hard")).isEqualTo(100.0);
        assertThat(scorer.weightedScore(result.accuracy(), "Normal")).isEqualTo(80.0);
        assertThat(scorer.weightedScore(result.accuracy(), "Easy")).isEqualTo(60.0);
    }

    @Test
    void nullOrUnknownDifficultyFallsBackToNormal() {
        assertThat(scorer.weightedScore(100.0, null)).isEqualTo(80.0);
        assertThat(scorer.weightedScore(100.0, "Insane")).isEqualTo(80.0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Easy", "Normal", "Hard"})
    void scoreNeverExceeds100OrDropsBelow0(String difficulty) {
        for (double accuracy : new double[]{-50, 0, 33.333, 99.99, 100, 150, 1e9}) {
            assertThat(scorer.weightedScore(accuracy, difficulty)).isBetween(0.0, 100.0);
        }
        assertThat(scorer.weightedScore(Double.NaN, difficulty)).isEqualTo(0.0);
    }

    @Test
    void scoreIsRoundedToTwoDecimals() {
        // 2 of 3 words correct -> 66.666...% accuracy; Normal -> 53.33
        DictationScorer.Result result = scorer.score("one two three", "one two four");
        assertThat(result.correctWords()).isEqualTo(2);
        assertThat(scorer.weightedScore(result.accuracy(), "Normal")).isEqualTo(53.33);
    }

    @Test
    void smallTyposOnLongerWordsAreAccepted() {
        DictationScorer.Result result = scorer.score("beautiful weather today", "beautifull wether today");
        assertThat(result.correctWords()).isEqualTo(3);
        assertThat(result.wordScores()).allMatch(WordScoreDto::isCorrect);
    }

    @Test
    void shortWordsTolerateOnlyOneEdit() {
        DictationScorer.Result result = scorer.score("cat", "dog");
        assertThat(result.correctWords()).isZero();
    }

    @Test
    void wrongAndMissingWordsAreMarkedIncorrect() {
        DictationScorer.Result result = scorer.score("the cat sat on the mat", "the dog sat");
        assertThat(result.totalWords()).isEqualTo(6);
        assertThat(result.correctWords()).isEqualTo(2);
        assertThat(result.wordScores().get(1).isCorrect()).isFalse();
        assertThat(result.wordScores().get(5).isCorrect()).isFalse();
    }

    @Test
    void emptyReferenceGivesZeroAccuracy() {
        DictationScorer.Result result = scorer.score("", "anything");
        assertThat(result.accuracy()).isEqualTo(0.0);
        assertThat(result.totalWords()).isZero();
    }
}
