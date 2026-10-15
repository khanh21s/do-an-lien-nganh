package com.parroto.practice.controller;

import com.parroto.common.dto.MessageResponse;
import com.parroto.common.exception.ApiException;
import com.parroto.practice.repository.GoalDayRepository;
import com.parroto.common.cache.CacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * T16, service-to-service (X-Internal-Token, see {@code InternalTokenFilter}): vocabulary-service
 * reports that a user met the game's daily goal on {@code day}; that day then counts for the
 * practice streak. Idempotent.
 */
@RestController
@RequestMapping("/internal/goal-days")
public class InternalGoalController {

    static final Set<String> SOURCES = Set.of("game");

    public record GoalDayRequest(UUID userId, LocalDate day, String source) {
    }

    private final GoalDayRepository goalDayRepository;
    private final CacheService cacheService;

    public InternalGoalController(GoalDayRepository goalDayRepository, CacheService cacheService) {
        this.goalDayRepository = goalDayRepository;
        this.cacheService = cacheService;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<MessageResponse> record(@RequestBody GoalDayRequest request) {
        if (request.userId() == null || request.day() == null || request.source() == null
                || !SOURCES.contains(request.source())) {
            throw ApiException.badRequest("userId, day and source (game) are required");
        }
        int inserted = goalDayRepository.insertIfAbsent(request.userId(), request.day(), request.source());
        if (inserted > 0) {
            cacheService.invalidateByPattern("practice:stats:" + request.userId());
        }
        return ResponseEntity.ok(new MessageResponse(inserted > 0 ? "Recorded" : "Already recorded"));
    }
}
