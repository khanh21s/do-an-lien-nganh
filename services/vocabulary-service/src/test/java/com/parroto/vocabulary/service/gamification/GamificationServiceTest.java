package com.parroto.vocabulary.service.gamification;

import com.parroto.common.exception.ApiException;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.vocabulary.dto.GamificationDtos.CheckInResult;
import com.parroto.vocabulary.dto.GamificationDtos.EventResult;
import com.parroto.vocabulary.dto.GamificationDtos.GamificationMe;
import com.parroto.vocabulary.dto.GamificationDtos.MissionState;
import com.parroto.vocabulary.dto.GamificationDtos.PracticeEventRequest;
import com.parroto.vocabulary.entity.GamificationProfile;
import com.parroto.vocabulary.repository.DiamondEventRepository;
import com.parroto.vocabulary.repository.GamificationProfileRepository;
import com.parroto.vocabulary.repository.XpEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Service rules against in-memory ledgers that honour the UNIQUE (source, ref_id) constraint. */
class GamificationServiceTest {

    record Row(UUID userId, String source, String refId, int amount, LocalDate day) {
    }

    /** Settable clock: 2026-10-15 10:00 in Vietnam by default. */
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-15T03:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final Map<String, Row> xp = new LinkedHashMap<>();
    private final Map<String, Row> diamonds = new LinkedHashMap<>();
    private final Map<UUID, GamificationProfile> profiles = new HashMap<>();
    private final MutableClock clock = new MutableClock();
    private GamificationService service;
    /** A user whose missions on 2026-10-15 include "play 1 round" but not "50 XP today". */
    private UUID userId;
    private AuthenticatedUser principal;
    private final LocalDate day = LocalDate.of(2026, 10, 15);

    @BeforeEach
    void setUp() {
        XpEventRepository xpRepo = mock(XpEventRepository.class);
        DiamondEventRepository diamondRepo = mock(DiamondEventRepository.class);
        GamificationProfileRepository profileRepo = mock(GamificationProfileRepository.class);

        when(xpRepo.insertIfAbsent(any(), anyString(), anyString(), anyInt(), any())).thenAnswer(inv ->
                insert(xp, new Row(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3), inv.getArgument(4))));
        when(xpRepo.totalXp(any())).thenAnswer(inv -> sum(xp, inv.getArgument(0), null, null));
        when(xpRepo.xpBetween(any(), any(), any())).thenAnswer(inv -> sum(xp, inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
        when(xpRepo.statsOn(any(), any())).thenAnswer(inv -> {
            Map<String, long[]> bySource = new LinkedHashMap<>();
            xp.values().stream().filter(r -> r.userId.equals(inv.getArgument(0)) && r.day.equals(inv.getArgument(1)))
                    .forEach(r -> {
                        long[] v = bySource.computeIfAbsent(r.source, k -> new long[2]);
                        v[0]++;
                        v[1] += r.amount;
                    });
            List<Object[]> rows = new ArrayList<>();
            bySource.forEach((s, v) -> rows.add(new Object[]{s, v[0], v[1]}));
            return rows;
        });
        when(diamondRepo.insertIfAbsent(any(), anyString(), anyString(), anyInt(), any())).thenAnswer(inv ->
                insert(diamonds, new Row(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3), inv.getArgument(4))));
        when(diamondRepo.balance(any())).thenAnswer(inv -> sum(diamonds, inv.getArgument(0), null, null));
        when(diamondRepo.refsOn(any(), anyString(), any())).thenAnswer(inv -> diamonds.values().stream()
                .filter(r -> r.userId.equals(inv.getArgument(0)) && r.source.equals(inv.getArgument(1)) && r.day.equals(inv.getArgument(2)))
                .map(Row::refId).toList());
        when(profileRepo.upsertDisplayName(any(), any())).thenAnswer(inv -> {
            profiles.computeIfAbsent(inv.getArgument(0), id -> GamificationProfile.builder().userId(id).build())
                    .setDisplayName(inv.getArgument(1));
            return 1;
        });
        when(profileRepo.findForUpdate(any())).thenAnswer(inv -> Optional.ofNullable(profiles.get(inv.<UUID>getArgument(0))));
        when(profileRepo.findById(any())).thenAnswer(inv -> Optional.ofNullable(profiles.get(inv.<UUID>getArgument(0))));
        when(profileRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new GamificationService(xpRepo, diamondRepo, profileRepo, clock);
        userId = IntStream.range(0, 10_000).mapToObj(i -> new UUID(42, i))
                .filter(u -> Mission.forDay(u, day).contains(Mission.GAME_1) && !Mission.forDay(u, day).contains(Mission.XP_50))
                .findFirst().orElseThrow();
        principal = new AuthenticatedUser(userId.toString(), "khanh@example.com", "B1");
    }

    private static int insert(Map<String, Row> table, Row row) {
        return table.putIfAbsent(row.source + "|" + row.refId, row) == null ? 1 : 0;
    }

    private static long sum(Map<String, Row> table, UUID user, LocalDate from, LocalDate to) {
        return table.values().stream().filter(r -> r.userId.equals(user)
                        && (from == null || !r.day.isBefore(from)) && (to == null || !r.day.isAfter(to)))
                .mapToLong(Row::amount).sum();
    }

    private PracticeEventRequest dictation(UUID sentence, double accuracy, String at) {
        return new PracticeEventRequest(userId, "DICTATION", UUID.randomUUID(), UUID.randomUUID(), sentence,
                accuracy, accuracy * 0.8, OffsetDateTime.parse(at));
    }

    @Test
    void practiceEventIsIdempotentAndLimitedOncePerSentencePerDay() {
        UUID sentence = UUID.randomUUID();
        EventResult first = service.recordPracticeEvent(dictation(sentence, 80, "2026-10-15T09:00:00+07:00"));
        assertThat(first.xpGained()).isEqualTo(5);
        assertThat(first.day()).isEqualTo(day);

        // the same attempt delivered twice, then a new 100 % attempt of the same sentence that day
        EventResult duplicate = service.recordPracticeEvent(dictation(sentence, 80, "2026-10-15T09:00:00+07:00"));
        assertThat(duplicate.xpGained()).isZero();
        assertThat(duplicate.awards()).allMatch(a -> !a.granted());
        EventResult perfect = service.recordPracticeEvent(dictation(sentence, 100, "2026-10-15T21:00:00+07:00"));
        assertThat(perfect.xpGained()).isEqualTo(5); // only the bonus is new
        assertThat(service.recordPracticeEvent(dictation(sentence, 100, "2026-10-15T22:00:00+07:00")).xpGained()).isZero();

        // next Vietnam day (00:30 local = 17:30Z of the previous UTC day): XP again
        assertThat(service.recordPracticeEvent(dictation(sentence, 100, "2026-10-15T17:30:00Z")).xpGained()).isEqualTo(10);
        assertThat(service.totalXp(userId)).isEqualTo(20);
        // shadowing of the same sentence is another mode
        EventResult shadow = service.recordPracticeEvent(new PracticeEventRequest(userId, "shadowing", null, null, sentence,
                90.0, 90.0, OffsetDateTime.parse("2026-10-15T09:05:00+07:00")));
        assertThat(shadow.xpGained()).isEqualTo(10);
    }

    @Test
    void invalidEventsAreRejected() {
        assertThatThrownBy(() -> service.recordPracticeEvent(new PracticeEventRequest(userId, "CHESS", null, null,
                UUID.randomUUID(), 1.0, 1.0, null))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.recordPracticeEvent(new PracticeEventRequest(null, "DICTATION", null, null,
                UUID.randomUUID(), 1.0, 1.0, null))).isInstanceOf(ApiException.class);
        assertThat(xp).isEmpty();
    }

    @Test
    void cardReviewGivesXpOncePerCardPerDay() {
        UUID card = UUID.randomUUID();
        assertThat(service.recordCardReview(userId, card)).isEqualTo(2);
        assertThat(service.recordCardReview(userId, card)).isZero();
        assertThat(service.recordCardReview(userId, UUID.randomUUID())).isEqualTo(2);
        clock.now = Instant.parse("2026-10-15T17:00:00Z"); // 00:00 on the 16th in Vietnam
        assertThat(service.recordCardReview(userId, card)).isEqualTo(2);
        assertThat(service.totalXp(userId)).isEqualTo(6);
    }

    @Test
    void completedMissionGrants30DiamondsExactlyOnce() {
        UUID round = UUID.randomUUID();
        assertThat(service.recordGameRound(userId, round, 10, day)).isEqualTo(10);
        assertThat(service.recordGameRound(userId, round, 10, day)).isZero(); // finish retried
        assertThat(sum(diamonds, userId, null, null)).isEqualTo(30);
        service.recordGameRound(userId, UUID.randomUUID(), 5, day);
        assertThat(sum(diamonds, userId, null, null)).isEqualTo(30);

        GamificationMe me = service.me(principal);
        MissionState game = me.missions().stream().filter(m -> m.code().equals("GAME_1")).findFirst().orElseThrow();
        assertThat(game.completed()).isTrue();
        assertThat(game.progress()).isEqualTo(1);
        assertThat(me.missions()).hasSize(3);
        assertThat(me.diamonds()).isEqualTo(30);
        assertThat(me.todayXp()).isEqualTo(15);
        assertThat(me.xp()).isEqualTo(15);
    }

    @Test
    void checkInOncePerVietnamDayWithStreakAndSeventhDayBonus() {
        // 23:59 Vietnam time on the 15th
        clock.now = Instant.parse("2026-10-15T16:59:00Z");
        CheckInResult first = service.checkIn(principal);
        assertThat(first.alreadyCheckedIn()).isFalse();
        assertThat(first.diamondsGained()).isEqualTo(50);
        assertThat(first.streak()).isEqualTo(1);
        CheckInResult again = service.checkIn(principal);
        assertThat(again.alreadyCheckedIn()).isTrue();
        assertThat(again.diamondsGained()).isZero();
        assertThat(again.diamonds()).isEqualTo(50);

        // two minutes later it is the 16th in Vietnam (still the 15th in UTC): a new check-in
        clock.now = Instant.parse("2026-10-15T17:01:00Z");
        assertThat(service.checkIn(principal).streak()).isEqualTo(2);

        CheckInResult last = null;
        for (int d = 1; d <= 5; d++) {
            clock.now = Instant.parse("2026-10-15T17:01:00Z").plusSeconds(86_400L * d);
            last = service.checkIn(principal);
        }
        assertThat(last.streak()).isEqualTo(7);
        assertThat(last.diamondsGained()).isEqualTo(150);
        assertThat(last.bonusDiamonds()).isEqualTo(100);
        assertThat(last.diamonds()).isEqualTo(7 * 50 + 100);

        // a missed day restarts the streak
        clock.now = Instant.parse("2026-10-15T17:01:00Z").plusSeconds(86_400L * 7);
        assertThat(service.checkIn(principal).streak()).isEqualTo(1);
        assertThat(profiles.get(userId).getDisplayName()).isEqualTo("kha***");
    }

    @Test
    void checkInStateShowsTodaysRewardAndDaysToBonus() {
        GamificationProfile p = GamificationProfile.builder().userId(userId).checkinStreak(6)
                .lastCheckinDay(day.minusDays(1)).build();
        var pending = GamificationService.checkInState(p, day);
        assertThat(pending.checkedInToday()).isFalse();
        assertThat(pending.streak()).isEqualTo(6);
        assertThat(pending.todayReward()).isEqualTo(150);
        assertThat(pending.daysToBonus()).isEqualTo(1);

        p.setCheckinStreak(7);
        p.setLastCheckinDay(day);
        var done = GamificationService.checkInState(p, day);
        assertThat(done.checkedInToday()).isTrue();
        assertThat(done.todayReward()).isEqualTo(150);
        assertThat(done.daysToBonus()).isEqualTo(7);

        var fresh = GamificationService.checkInState(null, day);
        assertThat(fresh.streak()).isZero();
        assertThat(fresh.todayReward()).isEqualTo(50);
        assertThat(fresh.daysToBonus()).isEqualTo(7);

        p.setLastCheckinDay(day.minusDays(3)); // broken streak
        assertThat(GamificationService.checkInState(p, day).streak()).isZero();
    }

    @Test
    void meReportsLevelOfTheSharedTotal() {
        service.recordGameRound(userId, UUID.randomUUID(), 340, day.minusDays(3)); // e.g. migrated game XP
        service.recordCardReview(userId, UUID.randomUUID());
        GamificationMe me = service.me(principal);
        assertThat(me.xp()).isEqualTo(342);
        assertThat(me.level()).isEqualTo(3);
        assertThat(me.levelXp()).isEqualTo(42);
        assertThat(me.levelXpNeeded()).isEqualTo(300);
        assertThat(me.todayXp()).isEqualTo(2);
        assertThat(me.weekXp()).isEqualTo(342); // Mon 12th – Sun 18th
        assertThat(me.today()).isEqualTo(day);
    }

    @Test
    void onlyTheWeeklyPeriodIsSupported() {
        assertThatThrownBy(() -> service.leaderboard("year", principal)).isInstanceOf(ApiException.class);
    }
}
