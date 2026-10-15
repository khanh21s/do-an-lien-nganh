package com.parroto.practice.service;

import com.parroto.common.cache.CacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parroto.practice.dto.DictationAttemptRequest;
import com.parroto.practice.dto.DictationAttemptResponse;
import com.parroto.practice.entity.PracticeAttempt;
import com.parroto.practice.repository.PracticeAttemptRepository;
import com.parroto.common.security.AuthenticatedUser;
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

    public DictationService(ContentServiceClient contentServiceClient, DictationScorer scorer,
                             PracticeAttemptRepository attemptRepository, ObjectMapper objectMapper,
                             CacheService cacheService) {
        this.contentServiceClient = contentServiceClient;
        this.scorer = scorer;
        this.attemptRepository = attemptRepository;
        this.objectMapper = objectMapper;
        this.cacheService = cacheService;
    }

    @Transactional
    public DictationAttemptResponse submitAttempt(AuthenticatedUser user, DictationAttemptRequest request) {
        String referenceText = contentServiceClient.fetchSentenceText(request.lessonId(), request.sentenceId());
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
                .build();
    }

    private BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
