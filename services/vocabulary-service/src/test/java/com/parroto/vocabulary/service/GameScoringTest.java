package com.parroto.vocabulary.service;

import com.parroto.vocabulary.entity.GameResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameScoringTest {

    @Test
    void basePointsFollowTheLevel() {
        assertThat(GameScoring.basePoints("A1")).isEqualTo(10);
        assertThat(GameScoring.basePoints("b1")).isEqualTo(15);
        assertThat(GameScoring.basePoints("B2")).isEqualTo(20);
        assertThat(GameScoring.basePoints("C1")).isEqualTo(30);
        assertThat(GameScoring.basePoints(null)).isEqualTo(15);
    }

    @Test
    void proposalExampleB2WithCombo10In2Seconds() {
        // combo 10 before -> 11th correct in a row: 20 × 2 × 1 + 5 = 45
        GameScoring.Outcome o = GameScoring.score(GameResult.CORRECT, "B2", 10, 0, 2_000);
        assertThat(o.points()).isEqualTo(45);
        assertThat(o.combo()).isEqualTo(11);
        assertThat(o.multiplier()).isEqualTo(2.0);
        assertThat(o.speedBonus()).isEqualTo(5);
    }

    @Test
    void fifthCorrectInARowGetsTheFirstMultiplier() {
        assertThat(GameScoring.score(GameResult.CORRECT, "A1", 3, 0, 10_000).points()).isEqualTo(10);
        assertThat(GameScoring.score(GameResult.CORRECT, "A1", 4, 0, 10_000).points()).isEqualTo(15);
        assertThat(GameScoring.score(GameResult.CORRECT, "A1", 19, 0, 10_000).points()).isEqualTo(30);
    }

    @Test
    void hintsReducePointsAndCancelTheSpeedBonus() {
        assertThat(GameScoring.score(GameResult.CORRECT, "B2", 0, 1, 1_000).points()).isEqualTo(16);
        assertThat(GameScoring.score(GameResult.CORRECT, "B2", 0, 2, 1_000).points()).isEqualTo(10);
        assertThat(GameScoring.score(GameResult.CORRECT, "B2", 0, 3, 1_000).points()).isEqualTo(5);
        assertThat(GameScoring.score(GameResult.CORRECT, "B2", 0, 0, 4_000).points()).isEqualTo(22);
    }

    @Test
    void nearKeepsTheComboWithHalfPointsAndMissesResetIt() {
        GameScoring.Outcome near = GameScoring.score(GameResult.NEAR, "B2", 10, 0, 1_000);
        assertThat(near.points()).isEqualTo(20);
        assertThat(near.combo()).isEqualTo(10);
        assertThat(GameScoring.score(GameResult.WRONG, "B2", 10, 0, 1_000).combo()).isZero();
        assertThat(GameScoring.score(GameResult.SKIPPED, "B2", 10, 0, 1_000).points()).isZero();
    }

    @Test
    void srsQualityMapping() {
        assertThat(GameScoring.srsQuality(GameResult.WRONG, 0)).isEqualTo(1);
        assertThat(GameScoring.srsQuality(GameResult.SKIPPED, 0)).isEqualTo(1);
        assertThat(GameScoring.srsQuality(GameResult.NEAR, 0)).isEqualTo(3);
        assertThat(GameScoring.srsQuality(GameResult.CORRECT, 2)).isEqualTo(3);
        assertThat(GameScoring.srsQuality(GameResult.CORRECT, 0)).isEqualTo(4);
    }
}
