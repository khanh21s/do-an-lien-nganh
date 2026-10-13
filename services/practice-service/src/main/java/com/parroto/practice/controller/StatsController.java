package com.parroto.practice.controller;

import com.parroto.practice.dto.ActivityDayResponse;
import com.parroto.practice.dto.LessonStatsResponse;
import com.parroto.practice.dto.StatsResponse;
import com.parroto.practice.dto.StreakResponse;
import com.parroto.practice.dto.TrendingErrorResponse;
import com.parroto.practice.security.AuthenticatedUser;
import com.parroto.practice.service.StatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/practice")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> stats(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(statsService.getStats(principal.userUuid()));
    }

    @GetMapping("/stats/by-lesson/{lessonId}")
    public ResponseEntity<LessonStatsResponse> statsByLesson(@AuthenticationPrincipal AuthenticatedUser principal,
                                                               @PathVariable UUID lessonId) {
        return ResponseEntity.ok(statsService.getLessonStats(principal.userUuid(), lessonId));
    }

    /** PRA-03: heatmap data, exactly {@code days} entries (1–365, default 90), Asia/Ho_Chi_Minh days. */
    @GetMapping("/stats/activity")
    public ResponseEntity<List<ActivityDayResponse>> activity(@AuthenticationPrincipal AuthenticatedUser principal,
                                                              @RequestParam(defaultValue = "90") int days) {
        return ResponseEntity.ok(statsService.getActivity(principal.userUuid(), days));
    }

    @GetMapping("/stats/trending-errors")
    public ResponseEntity<List<TrendingErrorResponse>> trendingErrors(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(statsService.getTrendingErrors(principal.userUuid()));
    }

    @GetMapping("/streak")
    public ResponseEntity<StreakResponse> streak(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(statsService.getStreak(principal.userUuid()));
    }
}
