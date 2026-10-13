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
import com.parroto.practice.service.speech.PronunciationScorer;
import com.parroto.practice.service.storage.RecordingStorage;
import com.parroto.practice.util.WavUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class ShadowingService {

    /** Max size of a local-mode upload (30 s of 16 kHz mono 16-bit PCM is ~0.96 MB). */
    public static final int MAX_UPLOAD_BYTES = 2 * 1024 * 1024;

    private final ContentServiceClient contentServiceClient;
    private final RecordingStorage storage;
    private final PronunciationScorer pronunciationScorer;
    private final PracticeAttemptRepository attemptRepository;
    private final ObjectMapper objectMapper;
    private final CacheService cacheService;

    public ShadowingService(ContentServiceClient contentServiceClient, RecordingStorage storage,
                            PronunciationScorer pronunciationScorer, PracticeAttemptRepository attemptRepository,
                            ObjectMapper objectMapper, CacheService cacheService) {
        this.contentServiceClient = contentServiceClient;
        this.storage = storage;
        this.pronunciationScorer = pronunciationScorer;
        this.attemptRepository = attemptRepository;
        this.objectMapper = objectMapper;
        this.cacheService = cacheService;
    }

    public ShadowingUploadUrlResponse requestUploadUrl(AuthenticatedUser user, ShadowingUploadUrlRequest request) {
        contentServiceClient.assertLessonExists(request.lessonId());
        return ShadowingUploadUrlResponse.from(storage.createUpload(user.userUuid()));
    }

    /** The gateway upload endpoint only exists in local storage mode; in S3 mode it is a 404. */
    public void requireLocalUploads() {
        if (!"local".equals(storage.provider())) {
            throw ApiException.notFound("Not found");
        }
    }

    /**
     * Local storage mode only: receives the raw WAV body uploaded through the gateway and
     * stores it under the caller's own key. In S3 mode the endpoint does not exist (404).
     */
    public void uploadLocalRecording(AuthenticatedUser user, String recordingId, byte[] wav) {
        requireLocalUploads();
        String id = RecordingStorage.requireValidRecordingId(recordingId);
        if (wav == null || wav.length == 0) {
            throw ApiException.badRequest("Bản ghi trống");
        }
        if (wav.length > MAX_UPLOAD_BYTES) {
            throw ApiException.badRequest("Bản ghi vượt quá 2 MB");
        }
        WavUtils.validate(wav);
        storage.putLocal(user.userUuid(), id, wav);
    }

    // Not @Transactional on purpose: the (possibly slow) Azure call must not hold a DB connection;
    // saveAndFlush runs in its own transaction.
    public ShadowingAttemptResponse submitAttempt(AuthenticatedUser user, ShadowingAttemptRequest request) {
        String recordingId = RecordingStorage.requireValidRecordingId(request.recordingId());
        String referenceText = contentServiceClient.fetchSentenceText(request.lessonId(), request.sentenceId());
        String key = RecordingStorage.keyFor(user.userUuid(), recordingId);
        byte[] audio = storage.read(key);
        if (audio.length == 0) {
            throw ApiException.badRequest("Bản ghi trống");
        }
        WavUtils.WavInfo wavInfo = WavUtils.validate(audio);

        PronunciationScorer.AssessmentResult result = pronunciationScorer.assess(audio, referenceText, recordingId);
        double score = clampScore(result.overallAccuracy());

        BigDecimal duration = request.duration() != null
                ? request.duration()
                : BigDecimal.valueOf(wavInfo.durationSeconds()).setScale(2, RoundingMode.HALF_UP);

        PracticeAttempt attempt = PracticeAttempt.builder()
                .userId(user.userUuid())
                .lessonId(request.lessonId())
                .sentenceId(request.sentenceId())
                .mode("shadowing")
                .score(round(score))
                .accuracy(round(score))
                .wordScores(objectMapper.valueToTree(result.wordScores()))
                .audioRecordingUrl(key)
                .duration(duration)
                .build();
        attempt = attemptRepository.saveAndFlush(attempt);
        cacheService.invalidateByPattern("practice:stats:" + user.userId());

        return ShadowingAttemptResponse.builder()
                .attemptId(attempt.getId())
                .recordingUrl("/practice/shadowing/%s/recording".formatted(attempt.getId()))
                .score(score)
                .accuracy(score)
                .duration(duration.doubleValue())
                .wordScores(result.wordScores())
                .createdAt(attempt.getCreatedAt())
                .scorer(result.scorer())
                .build();
    }

    /** SHA-04: only the owner may fetch a recording; anyone else gets 404 (no existence leak). */
    public void streamRecording(AuthenticatedUser user, UUID attemptId, HttpServletResponse response) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, user.userUuid())
                .orElseThrow(() -> ApiException.notFound("Attempt not found"));
        if (attempt.getAudioRecordingUrl() == null) {
            throw ApiException.notFound("No recording for this attempt");
        }
        storage.stream(attempt.getAudioRecordingUrl(), response);
    }

    private static double clampScore(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(100.0, value));
    }

    private BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
