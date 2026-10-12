package com.parroto.practice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parroto.practice.dto.ShadowingAttemptRequest;
import com.parroto.practice.dto.ShadowingAttemptResponse;
import com.parroto.practice.dto.ShadowingUploadUrlRequest;
import com.parroto.practice.dto.ShadowingUploadUrlResponse;
import com.parroto.practice.entity.PracticeAttempt;
import com.parroto.practice.exception.ApiException;
import com.parroto.practice.repository.PracticeAttemptRepository;
import com.parroto.practice.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class ShadowingService {

    private final ContentServiceClient contentServiceClient;
    private final ShadowingStorageService storageService;
    private final AzureSpeechClient azureSpeechClient;
    private final PracticeAttemptRepository attemptRepository;
    private final ObjectMapper objectMapper;
    private final CacheService cacheService;

    public ShadowingService(ContentServiceClient contentServiceClient, ShadowingStorageService storageService,
                             AzureSpeechClient azureSpeechClient, PracticeAttemptRepository attemptRepository,
                             ObjectMapper objectMapper, CacheService cacheService) {
        this.contentServiceClient = contentServiceClient;
        this.storageService = storageService;
        this.azureSpeechClient = azureSpeechClient;
        this.attemptRepository = attemptRepository;
        this.objectMapper = objectMapper;
        this.cacheService = cacheService;
    }

    public ShadowingUploadUrlResponse requestUploadUrl(AuthenticatedUser user, ShadowingUploadUrlRequest request) {
        contentServiceClient.assertLessonExists(request.lessonId());
        ShadowingStorageService.PresignedUpload upload = storageService.createUploadUrl(user.userUuid());
        return new ShadowingUploadUrlResponse(upload.uploadUrl(), upload.recordingId(), 3600);
    }

    @Transactional
    public ShadowingAttemptResponse submitAttempt(AuthenticatedUser user, ShadowingAttemptRequest request) {
        String referenceText = contentServiceClient.fetchSentenceText(request.lessonId(), request.sentenceId());
        String key = storageService.keyFor(user.userUuid(), request.recordingId());
        byte[] audio = storageService.downloadBytes(key);
        if (audio.length == 0) {
            throw ApiException.badRequest("Recording is empty");
        }

        AzureSpeechClient.AssessmentResult result = azureSpeechClient.assessPronunciation(audio, referenceText);

        PracticeAttempt attempt = PracticeAttempt.builder()
                .userId(user.userUuid())
                .lessonId(request.lessonId())
                .sentenceId(request.sentenceId())
                .mode("shadowing")
                .score(round(result.overallAccuracy()))
                .accuracy(round(result.overallAccuracy()))
                .wordScores(objectMapper.valueToTree(result.wordScores()))
                .audioRecordingUrl(key)
                .duration(request.duration())
                .build();
        attempt = attemptRepository.saveAndFlush(attempt);
        cacheService.invalidateByPattern("practice:stats:" + user.userId());

        return ShadowingAttemptResponse.builder()
                .attemptId(attempt.getId())
                .recordingUrl("/practice/shadowing/%s/recording".formatted(attempt.getId()))
                .score(result.overallAccuracy())
                .accuracy(result.overallAccuracy())
                .duration(request.duration() != null ? request.duration().doubleValue() : 0.0)
                .wordScores(result.wordScores())
                .createdAt(attempt.getCreatedAt())
                .build();
    }

    public void streamRecording(UUID attemptId, jakarta.servlet.http.HttpServletResponse response) {
        PracticeAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> ApiException.notFound("Attempt not found"));
        if (attempt.getAudioRecordingUrl() == null) {
            throw ApiException.notFound("No recording for this attempt");
        }
        storageService.stream(attempt.getAudioRecordingUrl(), response);
    }

    private BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
