package com.parroto.vocabulary.controller;

import com.parroto.vocabulary.dto.*;
import com.parroto.vocabulary.security.AuthenticatedUser;
import com.parroto.vocabulary.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/vocabs/user")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/review-queue")
    public ResponseEntity<ReviewQueueResponse> reviewQueue(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(reviewService.getReviewQueue(principal.userUuid()));
    }

    @PostMapping("/review")
    public ResponseEntity<SubmitReviewResponse> submitReview(@AuthenticationPrincipal AuthenticatedUser principal,
                                                               @Valid @RequestBody SubmitReviewRequest request) {
        return ResponseEntity.ok(reviewService.submitReview(principal.userUuid(), request));
    }

    @GetMapping("/stats")
    public ResponseEntity<VocabUserStatsResponse> stats(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(reviewService.getUserStats(principal.userUuid()));
    }

    @GetMapping("/learned")
    public ResponseEntity<PageResponse<LearnedWordResponse>> learned(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String searchWord,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(reviewService.getLearnedWords(principal.userUuid(), searchWord, page, limit));
    }

    @PostMapping("/add-from-lesson")
    public ResponseEntity<AddFromLessonResponse> addFromLesson(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                 @Valid @RequestBody AddFromLessonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.addFromLesson(principal.userUuid(), request));
    }

    @DeleteMapping("/{userVocabId}")
    public ResponseEntity<MessageResponse> remove(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @PathVariable UUID userVocabId) {
        reviewService.removeFromNotebook(principal.userUuid(), userVocabId);
        return ResponseEntity.ok(new MessageResponse("Removed from notebook"));
    }
}
