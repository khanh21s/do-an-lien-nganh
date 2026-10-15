package com.parroto.content.controller;

import com.parroto.common.dto.MessageResponse;
import com.parroto.common.dto.PageResponse;
import com.parroto.content.dto.*;
import com.parroto.common.security.AdminGuard;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.content.service.LessonService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/lessons")
public class LessonController {

    private final LessonService lessonService;
    private final AdminGuard adminGuard;

    public LessonController(LessonService lessonService, AdminGuard adminGuard) {
        this.lessonService = lessonService;
        this.adminGuard = adminGuard;
    }

    @GetMapping
    public ResponseEntity<PageResponse<LessonSummaryResponse>> list(
            @RequestParam(required = false) String cefrLevel,
            @RequestParam(required = false) String topicTag,
            @RequestParam(required = false) Boolean isPro,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(lessonService.listLessons(cefrLevel, topicTag, isPro, page, limit));
    }

    @GetMapping("/search")
    public ResponseEntity<PageResponse<LessonSummaryResponse>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(lessonService.searchLessons(q, page, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LessonDetailResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(lessonService.getLesson(id));
    }

    @PostMapping
    public ResponseEntity<LessonDetailResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                         @Valid @RequestBody CreateLessonRequest request) {
        adminGuard.requireAdmin(principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(lessonService.createLesson(request));
    }

    @PostMapping("/{id}/sentences")
    public ResponseEntity<java.util.List<SentenceResponse>> addSentences(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody CreateSentencesRequest request) {
        adminGuard.requireAdmin(principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(lessonService.addSentences(id, request));
    }

    @PutMapping("/{id}/sentences/{sentenceId}")
    public ResponseEntity<SentenceResponse> updateSentence(@AuthenticationPrincipal AuthenticatedUser principal,
                                                           @PathVariable UUID id,
                                                           @PathVariable UUID sentenceId,
                                                           @Valid @RequestBody UpdateSentenceRequest request) {
        adminGuard.requireAdmin(principal);
        return ResponseEntity.ok(lessonService.updateSentence(id, sentenceId, request));
    }

    @DeleteMapping("/{id}/sentences/{sentenceId}")
    public ResponseEntity<MessageResponse> deleteSentence(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @PathVariable UUID id,
                                                          @PathVariable UUID sentenceId) {
        adminGuard.requireAdmin(principal);
        lessonService.deleteSentence(id, sentenceId);
        return ResponseEntity.ok(new MessageResponse("Đã xoá câu"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LessonDetailResponse> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                                         @PathVariable UUID id,
                                                         @Valid @RequestBody UpdateLessonRequest request) {
        adminGuard.requireAdmin(principal);
        return ResponseEntity.ok(lessonService.updateLesson(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @PathVariable UUID id) {
        adminGuard.requireAdmin(principal);
        lessonService.deleteLesson(id);
        return ResponseEntity.ok(new MessageResponse("Lesson deleted successfully"));
    }
}
