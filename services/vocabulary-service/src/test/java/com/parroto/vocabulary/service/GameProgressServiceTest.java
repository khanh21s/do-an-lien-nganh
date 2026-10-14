package com.parroto.vocabulary.service;

import com.parroto.vocabulary.dto.GameDtos.Leaderboard;
import com.parroto.vocabulary.dto.GameDtos.Progress;
import com.parroto.vocabulary.entity.GameBadge;
import com.parroto.vocabulary.entity.GameMode;
import com.parroto.vocabulary.entity.GameProfile;
import com.parroto.vocabulary.entity.GameSession;
import com.parroto.vocabulary.exception.ApiException;
import com.parroto.vocabulary.repository.GameAnswerRepository;
import com.parroto.vocabulary.repository.GameBadgeRepository;
import com.parroto.vocabulary.repository.GameProfileRepository;
import com.parroto.vocabulary.repository.GameSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GameProgressServiceTest {

    // 2026-10-14 23:30 in Vietnam = 16:30 UTC (Wednesday)
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T16:30:00Z"), ZoneOffset.UTC);
    private final UUID userId = UUID.randomUUID();
    private final List<GameBadge> badges = new ArrayList<>();
    private GameProfileRepository profileRepository;
    private GameBadgeRepository badgeRepository;
    private GameSessionRepository sessionRepository;
    private GameAnswerRepository answerRepository;
    private PracticeClient practiceClient;
    private GameProgressService service;
    private GameProfile profile;

    @BeforeEach
    void setUp() {
        profileRepository = mock(GameProfileRepository.class);
        badgeRepository = mock(GameBadgeRepository.class);
        sessionRepository = mock(GameSessionRepository.class);
        answerRepository = mock(GameAnswerRepository.class);
        practiceClient = mock(PracticeClient.class);
        service = new GameProgressService(profileRepository, badgeRepository, sessionRepository, answerRepository,
                practiceClient, clock);
        profile = GameProfile.builder().userId(userId).xp(0L).build();
        when(profileRepository.findForUpdate(userId)).thenReturn(Optional.of(profile));
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(badgeRepository.findByUserId(userId)).thenAnswer(inv -> List.copyOf(badges));
        when(badgeRepository.save(any(GameBadge.class))).thenAnswer(inv -> {
            badges.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(practiceClient.recordGoalDay(any(), any())).thenReturn(true);
    }

    private GameSession round(GameMode mode, int score, int bestCombo) {
        return GameSession.builder().id(UUID.randomUUID()).userId(userId).mode(mode.name()).score(score)
                .bestCombo(bestCombo).correctCount(0).nearCount(0).fastCount(0).ranked(true)
                .playDate(LocalDate.of(2026, 10, 14)).build();
    }

    @Test
    void levelsGrowBy100XpPerLevel() {
        assertThat(GameProgressService.levelFor(0)).isEqualTo(1);
        assertThat(GameProgressService.levelFor(99)).isEqualTo(1);
        assertThat(GameProgressService.levelFor(100)).isEqualTo(2);
        assertThat(GameProgressService.levelFor(299)).isEqualTo(2);
        assertThat(GameProgressService.levelFor(300)).isEqualTo(3);
        assertThat(GameProgressService.levelFor(1000)).isEqualTo(5);
        Progress p = GameProgressService.progress(GameProfile.builder().xp(350L).dailyGoal(200).build(), 250);
        assertThat(p.level()).isEqualTo(3);
        assertThat(p.levelXp()).isEqualTo(50);
        assertThat(p.levelXpNeeded()).isEqualTo(300);
        assertThat(p.goalMet()).isTrue();
    }

    @Test
    void todayAndWeekUseVietnamTime() {
        assertThat(service.today()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(GameProgressService.weekStart(LocalDate.of(2026, 10, 18))).isEqualTo(LocalDate.of(2026, 10, 12));
    }

    @Test
    void finishedRoundAddsXpReportsTheGoalOnceAndAwardsBadges() {
        when(sessionRepository.pointsOn(userId, LocalDate.of(2026, 10, 14))).thenReturn(250L);
        GameSession r = round(GameMode.SPRINT, 250, 12);

        service.onRoundFinished(r, OffsetDateTime.now(clock));

        assertThat(profile.getXp()).isEqualTo(250);
        verify(practiceClient).recordGoalDay(userId, LocalDate.of(2026, 10, 14));
        assertThat(profile.getGoalRecordedDay()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(badges).extracting(GameBadge::getCode).containsExactlyInAnyOrder("FIRST_GAME", "COMBO_10");
        assertThat(service.badgesOf(r)).extracting(b -> b.code()).containsExactlyInAnyOrder("FIRST_GAME", "COMBO_10");

        // second round the same day: goal already reported, badges not awarded twice
        service.onRoundFinished(round(GameMode.SPRINT, 30, 2), OffsetDateTime.now(clock));
        verify(practiceClient, times(1)).recordGoalDay(any(), any());
        assertThat(badges).hasSize(2);
        assertThat(profile.getXp()).isEqualTo(280);
    }

    @Test
    void goalReportIsRetriedWhenPracticeServiceWasDown() {
        when(sessionRepository.pointsOn(any(), any())).thenReturn(500L);
        when(practiceClient.recordGoalDay(any(), any())).thenReturn(false, true);
        service.onRoundFinished(round(GameMode.SPRINT, 500, 0), OffsetDateTime.now(clock));
        assertThat(profile.getGoalRecordedDay()).isNull();
        service.onRoundFinished(round(GameMode.SPRINT, 10, 0), OffsetDateTime.now(clock));
        assertThat(profile.getGoalRecordedDay()).isEqualTo(LocalDate.of(2026, 10, 14));
    }

    @Test
    void modeSpecificBadges() {
        GameSession perfect = round(GameMode.DAILY, 300, 10);
        perfect.setCorrectCount(10);
        when(sessionRepository.countByUserIdAndModeAndStatus(userId, "DAILY", "FINISHED")).thenReturn(7L);
        service.onRoundFinished(perfect, OffsetDateTime.now(clock));
        assertThat(badges).extracting(GameBadge::getCode).contains("DAILY_PERFECT", "DAILY_7", "COMBO_10");

        GameSession survival = round(GameMode.SURVIVAL, 600, 25);
        survival.setCorrectCount(30);
        survival.setFastCount(10);
        when(answerRepository.countCorrectOnLevel(userId, "B2")).thenReturn(100L);
        service.onRoundFinished(survival, OffsetDateTime.now(clock));
        assertThat(badges).extracting(GameBadge::getCode)
                .contains("SURVIVOR_30", "COMBO_20", "SPEED_10", "SCORE_500", "B2_MASTER");
        assertThat(service.allBadges(userId)).hasSize(BadgeCatalog.values().length)
                .allSatisfy(b -> assertThat(b.earnedAt()).isNotNull());
    }

    @Test
    void dailyGoalOptions() {
        when(sessionRepository.pointsOn(any(), any())).thenReturn(120L);
        Progress p = service.setDailyGoal(userId, 100);
        assertThat(p.dailyGoal()).isEqualTo(100);
        assertThat(p.goalMet()).isTrue();
        verify(practiceClient).recordGoalDay(userId, LocalDate.of(2026, 10, 14));
        assertThatThrownBy(() -> service.setDailyGoal(userId, 150)).isInstanceOf(ApiException.class);
    }

    @Test
    void leaderboardKeepsTheTopAndTheCallersRow() {
        UUID other = UUID.randomUUID();
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{other.toString(), "abc***", 300, 12, 1L, 25L});
        rows.add(new Object[]{userId.toString(), "me***", 40, 2, 22L, 25L});
        when(sessionRepository.leaderboard(eq("SPRINT"), eq(LocalDate.of(2026, 10, 12)), eq(LocalDate.of(2026, 10, 18)),
                eq(20), eq(userId))).thenReturn(rows);

        Leaderboard lb = service.leaderboard("sprint-week", userId);

        assertThat(lb.title()).isEqualTo("Nước rút tuần 12/10–18/10");
        assertThat(lb.entries()).hasSize(1);
        assertThat(lb.entries().get(0).displayName()).isEqualTo("abc***");
        assertThat(lb.me().rank()).isEqualTo(22);
        assertThat(lb.me().me()).isTrue();
        assertThat(lb.totalPlayers()).isEqualTo(25);
        assertThatThrownBy(() -> service.leaderboard("all-time", userId)).isInstanceOf(ApiException.class);
    }

    @Test
    void boardsAndDisplayNames() {
        assertThat(GameProgressService.boardOf(round(GameMode.DAILY, 0, 0))).isEqualTo("daily");
        assertThat(GameProgressService.boardOf(round(GameMode.SURVIVAL, 0, 0))).isEqualTo("survival-week");
        GameSession unranked = round(GameMode.SPRINT, 0, 0);
        unranked.setRanked(false);
        assertThat(GameProgressService.boardOf(unranked)).isNull();
        assertThat(GameProgressService.displayName("khanhdoan@gmail.com")).isEqualTo("kha***");
        assertThat(GameProgressService.displayName("ab@x.vn")).isEqualTo("a***");
        assertThat(GameProgressService.displayName(null)).isEqualTo("Người chơi");
    }
}
