package com.parroto.practice.service;

import com.parroto.common.cache.CacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parroto.practice.dto.DictationAttemptRequest;
import com.parroto.practice.dto.DictationAttemptResponse;
import com.parroto.practice.entity.PracticeAttempt;
import com.parroto.practice.repository.PracticeAttemptRepository;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.practice.dto.SentenceReviewOutcome;
import com.parroto.practice.service.review.ReviewRules;
import com.parroto.practice.service.review.SentenceReviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class DictationService {

    private final ContentServiceClient contentServiceClient;
    private final DictationScorer scorer;
    private final PracticeAttemptRepository attemptRepository;
    private final ObjectMapper objectMapper;
    private final CacheService cacheService;
    private final LessonAccessGuard lessonAccessGuard;
    private final SentenceReviewService sentenceReviewService;

    public DictationService(ContentServiceClient contentServiceClient, DictationScorer scorer,
                             PracticeAttemptRepository attemptRepository, ObjectMapper objectMapper,
                             CacheService cacheService, LessonAccessGuard lessonAccessGuard,
                             SentenceReviewService sentenceReviewService) {
        this.contentServiceClient = contentServiceClient;
        this.scorer = scorer;
        this.attemptRepository = attemptRepository;
        this.objectMapper = objectMapper;
        this.cacheService = cacheService;
        this.lessonAccessGuard = lessonAccessGuard;
        this.sentenceReviewService = sentenceReviewService;
    }

    @Transactional
    public DictationAttemptResponse submitAttempt(AuthenticatedUser user, DictationAttemptRequest request) {
        String referenceText = loadReferenceText(user, request);
        DictationScorer.Result result = scorer.score(referenceText, request.userInput());
        double weightedScore = scorer.weightedScore(result.accuracy(), request.difficulty());

        PracticeAttempt attempt = PracticeAttempt.builder()
                .userId(user.userUuid())
                .lessonId(request.lessonId())
                .sentenceId(request.sentenceId())
                .mode("dictation")
                .score(round(weightedScore))
                .accuracy(round(result.accuracy()))
                .wordScores(objectMapper.valueToTree(result.wordScores()))
                .difficulty(request.difficulty() == null ? "Normal" : request.difficulty())
                .build();
        // saveAndFlush: the response below reads @CreationTimestamp, populated only once the
        // INSERT actually executes (which a plain save() only guarantees at transaction commit).
        attempt = attemptRepository.saveAndFlush(attempt);
        cacheService.invalidateByPattern("practice:stats:" + user.userId());
        SentenceReviewOutcome review = updateReviewQueue(user, request, result.accuracy());

        return DictationAttemptResponse.builder()
                .attemptId(attempt.getId())
                .lessonId(attempt.getLessonId())
                .sentenceId(attempt.getSentenceId())
                .accuracy(result.accuracy())
                .score(weightedScore)
                .totalWords(result.totalWords())
                .correctWords(result.correctWords())
                .wordScores(result.wordScores())
                .createdAt(attempt.getCreatedAt())
                .review(review)
                .build();
    }

    /** P2-A1: reference text of the sentence; Pro lessons need premium (or admin) → else 403. */
    private String loadReferenceText(AuthenticatedUser user, DictationAttemptRequest request) {
        ContentServiceClient.PracticeTarget target = contentServiceClient.fetchSentence(request.lessonId(), request.sentenceId());
        lessonAccessGuard.requirePracticeAccess(user, target.lesson());
        return target.sentence().text();
    }

    /** P2-A2: accuracy < 100 queues the sentence; a review-screen attempt grades it (SM-2). */
    private SentenceReviewOutcome updateReviewQueue(AuthenticatedUser user, DictationAttemptRequest request, double accuracy) {
        return sentenceReviewService.recordAttempt(user.userUuid(), request.lessonId(), request.sentenceId(),
                ReviewRules.DICTATION, accuracy, request.reviewId());
    }

    private BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
