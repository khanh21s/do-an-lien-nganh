package com.parroto.practice.controller;

import com.parroto.common.dto.PageResponse;
import com.parroto.practice.dto.*;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.practice.service.AttemptQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/practice")
public class AttemptController {

    private final AttemptQueryService attemptQueryService;

    public AttemptController(AttemptQueryService attemptQueryService) {
        this.attemptQueryService = attemptQueryService;
    }

    @GetMapping("/attempts")
    public ResponseEntity<PageResponse<AttemptSummaryResponse>> listAttempts(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) UUID lessonId,
            @RequestParam(required = false) String mode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(attemptQueryService.listAttempts(principal.userUuid(), lessonId, mode, page, limit));
    }

    @GetMapping("/attempts/{id}")
    public ResponseEntity<AttemptDetailResponse> getAttempt(@AuthenticationPrincipal AuthenticatedUser principal,
                                                              @PathVariable UUID id) {
        return ResponseEntity.ok(attemptQueryService.getAttempt(principal.userUuid(), id));
    }

    @GetMapping("/lessons/{lessonId}/attempts")
    public ResponseEntity<LessonAttemptsResponse> getLessonAttempts(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                      @PathVariable UUID lessonId) {
        return ResponseEntity.ok(attemptQueryService.getLessonAttempts(principal.userUuid(), lessonId));
    }
}
