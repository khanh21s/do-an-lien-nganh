package com.parroto.vocabulary.service.gamification;

import com.parroto.vocabulary.service.gamification.XpRules.XpSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MissionTest {

    private final UUID user = UUID.fromString("3c2cc442-5870-4bec-b5aa-8a286fc0feb5");
    private final LocalDate day = LocalDate.of(2026, 10, 15);

    @Test
    void threeDistinctMissionsPickedDeterministicallyPerUserAndDay() {
        List<Mission> a = Mission.forDay(user, day);
        assertThat(a).hasSize(3).doesNotHaveDuplicates();
        assertThat(Mission.forDay(UUID.fromString(user.toString()), LocalDate.parse("2026-10-15"))).isEqualTo(a);
        // shown in pool order
        assertThat(a).isSorted();
    }

    @Test
    void pickVariesAcrossDaysAndUsers() {
        Set<List<Mission>> perDay = new HashSet<>();
        Set<List<Mission>> perUser = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            perDay.add(Mission.forDay(user, day.plusDays(i)));
            perUser.add(Mission.forDay(new UUID(0, i), day));
        }
        assertThat(perDay.size()).isGreaterThan(3);
        assertThat(perUser.size()).isGreaterThan(3);
        // every mission of the pool shows up sometimes
        Set<Mission> seen = new HashSet<>();
        perDay.forEach(seen::addAll);
        perUser.forEach(seen::addAll);
        assertThat(seen).containsExactlyInAnyOrder(Mission.values());
    }

    @Test
    void progressComesFromTheDaysLedger() {
        Mission.DayStats stats = new Mission.DayStats(Map.of(XpSource.DICTATION, 4L, XpSource.GAME, 1L), 49);
        assertThat(Mission.DICTATION_5.value(stats)).isEqualTo(4);
        assertThat(Mission.DICTATION_5.isComplete(stats)).isFalse();
        assertThat(Mission.GAME_1.isComplete(stats)).isTrue();
        assertThat(Mission.XP_50.isComplete(stats)).isFalse();
        assertThat(Mission.XP_50.isComplete(new Mission.DayStats(Map.of(), 50))).isTrue();
        assertThat(Mission.REVIEW_10.value(Mission.DayStats.EMPTY)).isZero();
        assertThat(Mission.GAME_1.rewardRef(user, day)).isEqualTo(user + ":2026-10-15:GAME_1");
    }
}
