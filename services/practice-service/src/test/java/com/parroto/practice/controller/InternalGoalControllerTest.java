package com.parroto.practice.controller;

import com.parroto.common.exception.ApiException;
import com.parroto.practice.repository.GoalDayRepository;
import com.parroto.common.cache.CacheService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InternalGoalControllerTest {

    private final GoalDayRepository repo = mock(GoalDayRepository.class);
    private final CacheService cache = mock(CacheService.class);
    private final InternalGoalController controller = new InternalGoalController(repo, cache);
    private final UUID user = UUID.randomUUID();
    private final LocalDate day = LocalDate.of(2026, 10, 14);

    @Test
    void recordsADayOnceAndRefreshesTheStatsCache() {
        when(repo.insertIfAbsent(user, day, "game")).thenReturn(1, 0);

        var first = controller.record(new InternalGoalController.GoalDayRequest(user, day, "game"));
        var again = controller.record(new InternalGoalController.GoalDayRequest(user, day, "game"));

        assertThat(first.getBody().message()).isEqualTo("Recorded");
        assertThat(again.getBody().message()).isEqualTo("Already recorded");
        verify(cache, times(1)).invalidateByPattern("practice:stats:" + user);
    }

    @Test
    void rejectsIncompleteOrUnknownSources() {
        assertThatThrownBy(() -> controller.record(new InternalGoalController.GoalDayRequest(user, null, "game")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> controller.record(new InternalGoalController.GoalDayRequest(user, day, "chess")))
                .isInstanceOf(ApiException.class);
        verify(repo, never()).insertIfAbsent(any(), any(), any());
    }
}
