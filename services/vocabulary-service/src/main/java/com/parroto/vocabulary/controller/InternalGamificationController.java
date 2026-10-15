package com.parroto.vocabulary.controller;

import com.parroto.vocabulary.dto.GamificationDtos.EventResult;
import com.parroto.vocabulary.dto.GamificationDtos.PracticeEventRequest;
import com.parroto.vocabulary.service.gamification.GamificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * P2-B, service-to-service only (X-Internal-Token checked by {@code InternalTokenFilter}; the
 * gateway never routes /internal): practice-service reports a saved dictation/shadowing attempt.
 * Idempotent: a repeated event is answered with {@code granted: false} and changes nothing.
 */
@RestController
@RequestMapping("/internal/gamification")
public class InternalGamificationController {

    private final GamificationService gamificationService;

    public InternalGamificationController(GamificationService gamificationService) {
        this.gamificationService = gamificationService;
    }

    @PostMapping("/events")
    public ResponseEntity<EventResult> event(@RequestBody PracticeEventRequest request) {
        return ResponseEntity.ok(gamificationService.recordPracticeEvent(request));
    }
}
