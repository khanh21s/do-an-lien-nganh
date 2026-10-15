package com.parroto.practice.controller;

import com.parroto.common.dto.MessageResponse;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.practice.dto.SentenceReviewItemResponse;
import com.parroto.practice.dto.SentenceReviewStatsResponse;
import com.parroto.practice.service.review.SentenceReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * P2-A2 sentence review ("Ôn câu sai"). Attempts are submitted through the existing
 * {@code POST /practice/dictation/attempt} / {@code /practice/shadowing/attempt} with {@code reviewId}.
 */
@RestController
@RequestMapping("/practice/review")
public class SentenceReviewController {

    private final SentenceReviewService reviewService;

    public SentenceReviewController(SentenceReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/queue")
    public ResponseEntity<List<SentenceReviewItemResponse>> queue(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                  @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(reviewService.queue(principal, limit));
    }

    @GetMapping("/stats")
    public ResponseEntity<SentenceReviewStatsResponse> stats(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(reviewService.stats(principal));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> remove(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @PathVariable UUID id) {
        reviewService.remove(principal, id);
        return ResponseEntity.ok(new MessageResponse("Đã xoá câu khỏi hàng ôn tập"));
    }
}
