package com.parroto.vocabulary.controller;

import com.parroto.common.dto.MessageResponse;
import com.parroto.common.dto.PageResponse;
import com.parroto.vocabulary.dto.*;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.vocabulary.service.PersonalWordService;
import com.parroto.vocabulary.service.ReviewService;
import com.parroto.vocabulary.service.gamification.GamificationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/vocabs/user")
public class ReviewController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    private final ReviewService reviewService;
    private final PersonalWordService personalWordService;
    // --- P2-B ---
    private final GamificationService gamificationService;

    public ReviewController(ReviewService reviewService, PersonalWordService personalWordService,
                            GamificationService gamificationService) {
        this.reviewService = reviewService;
        this.personalWordService = personalWordService;
        this.gamificationService = gamificationService;
    }

    @GetMapping("/review-queue")
    public ResponseEntity<ReviewQueueResponse> reviewQueue(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(reviewService.getReviewQueue(principal.userUuid()));
    }

    @PostMapping("/review")
    public ResponseEntity<SubmitReviewResponse> submitReview(@AuthenticationPrincipal AuthenticatedUser principal,
                                                               @Valid @RequestBody SubmitReviewRequest request) {
        SubmitReviewResponse response = reviewService.submitReview(principal.userUuid(), request);
        // P2-B: +2 XP once per card per day, after the review itself is committed; a ledger
        // failure must not turn a saved review into an error.
        try {
            gamificationService.recordCardReview(principal.userUuid(), response.userVocabId());
        } catch (DataAccessException e) {
            log.warn("Could not record review XP for user {}: {}", principal.userId(), e.getMessage());
        }
        return ResponseEntity.ok(response);
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

    /** T13: add a word of the user's own (validated in the service -> 400, not Bean Validation 422). */
    @PostMapping("/words")
    public ResponseEntity<LearnedWordResponse> createWord(@AuthenticationPrincipal AuthenticatedUser principal,
                                                            @RequestBody CreatePersonalWordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(personalWordService.create(principal.userUuid(), request));
    }

    @PutMapping("/words/{userVocabId}")
    public ResponseEntity<LearnedWordResponse> updateWord(@AuthenticationPrincipal AuthenticatedUser principal,
                                                            @PathVariable UUID userVocabId,
                                                            @RequestBody CreatePersonalWordRequest request) {
        return ResponseEntity.ok(personalWordService.update(principal.userUuid(), userVocabId, request));
    }

    @DeleteMapping("/{userVocabId}")
    public ResponseEntity<MessageResponse> remove(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @PathVariable UUID userVocabId) {
        personalWordService.remove(principal.userUuid(), userVocabId);
        return ResponseEntity.ok(new MessageResponse("Removed from notebook"));
    }
}
