package com.parroto.practice.service;

import com.parroto.practice.dto.ActivityDayResponse;
import com.parroto.practice.exception.ApiException;
import com.parroto.practice.repository.PracticeAttemptRepository;
import com.parroto.practice.util.AppTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatsActivityTest {

    @Test
    void fillsEveryDayOldestFirstWithZeros() {
        LocalDate first = LocalDate.of(2026, 7, 16);
        List<Object[]> rows = List.<Object[]>of(
                new Object[]{"2026-07-16", 3L, new BigDecimal("150")},
                new Object[]{"2026-10-13", 1L, new BigDecimal("29")});

        List<ActivityDayResponse> result = StatsService.buildActivity(rows, first, 90);

        assertThat(result).hasSize(90);
        assertThat(result.get(0)).isEqualTo(new ActivityDayResponse("2026-07-16", 3, 3)); // 150 s -> round(2.5) = 3
        assertThat(result.get(1)).isEqualTo(new ActivityDayResponse("2026-07-17", 0, 0));
        assertThat(result.get(89)).isEqualTo(new ActivityDayResponse("2026-10-13", 1, 0));  // 29 s -> 0 min
    }

    @Test
    void getActivityValidatesRangeAndReturnsExactlyDaysEntries() {
        PracticeAttemptRepository repo = mock(PracticeAttemptRepository.class);
        when(repo.dailyActivitySince(any(), any())).thenReturn(List.of());
        StatsService service = new StatsService(repo, new StreakCalculator(), mock(CacheService.class));
        UUID user = UUID.randomUUID();

        List<ActivityDayResponse> days = service.getActivity(user, 90);
        assertThat(days).hasSize(90);
        assertThat(days.get(89).date()).isEqualTo(AppTime.today().toString());

        for (int bad : new int[]{0, -1, 366, 400}) {
            assertThatThrownBy(() -> service.getActivity(user, bad))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(service.getActivity(user, 365)).hasSize(365);
    }

    @Test
    void singleDayWindow() {
        LocalDate today = LocalDate.of(2026, 10, 13);
        List<ActivityDayResponse> result = StatsService.buildActivity(List.of(), today, 1);
        assertThat(result).containsExactly(new ActivityDayResponse("2026-10-13", 0, 0));
    }
}
