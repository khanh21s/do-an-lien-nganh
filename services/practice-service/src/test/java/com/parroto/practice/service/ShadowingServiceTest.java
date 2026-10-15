package com.parroto.practice.service;

import com.parroto.common.cache.CacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parroto.practice.dto.ShadowingAttemptRequest;
import com.parroto.practice.dto.ShadowingAttemptResponse;
import com.parroto.practice.dto.ShadowingUploadUrlRequest;
import com.parroto.practice.dto.ShadowingUploadUrlResponse;
import com.parroto.practice.entity.PracticeAttempt;
import com.parroto.common.exception.ApiException;
import com.parroto.practice.repository.PracticeAttemptRepository;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.practice.service.speech.MockPronunciationScorer;
import com.parroto.practice.service.storage.LocalRecordingStorage;
import com.parroto.practice.service.storage.RecordingStorage;
import com.parroto.practice.util.WavFixtures;
import com.parroto.practice.util.WavUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShadowingServiceTest {

    @TempDir
    Path mediaRoot;

    private final UUID lessonId = UUID.randomUUID();
    private final UUID sentenceId = UUID.randomUUID();
    private final AuthenticatedUser alice = new AuthenticatedUser(UUID.randomUUID().toString(), "a@x.vn", "B1");
    private final AuthenticatedUser bob = new AuthenticatedUser(UUID.randomUUID().toString(), "b@x.vn", "B1");

    private PracticeAttemptRepository repo;
    private ShadowingService service;

    @BeforeEach
    void setUp() {
        ContentServiceClient content = mock(ContentServiceClient.class);
        when(content.fetchSentenceText(lessonId, sentenceId)).thenReturn("Nice to meet you.");
        repo = mock(PracticeAttemptRepository.class);
        when(repo.saveAndFlush(any())).thenAnswer(inv -> {
            PracticeAttempt a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            a.setCreatedAt(OffsetDateTime.now());
            return a;
        });
        service = new ShadowingService(content, new LocalRecordingStorage(mediaRoot), new MockPronunciationScorer(),
                repo, new ObjectMapper(), mock(CacheService.class));
    }

    @Test
    void localModeFullFlowWithoutKeys() throws Exception {
        ShadowingUploadUrlResponse upload = service.requestUploadUrl(alice, new ShadowingUploadUrlRequest(lessonId, sentenceId));
        assertThat(upload.storage()).isEqualTo("local");
        assertThat(upload.uploadRequiresAuth()).isTrue();
        assertThat(upload.uploadMethod()).isEqualTo("PUT");
        assertThat(upload.contentType()).isEqualTo("audio/wav");
        assertThat(upload.uploadUrl()).isEqualTo("/practice/shadowing/recordings/" + upload.recordingId());

        byte[] wav = WavFixtures.toneWav(2.0);
        service.uploadLocalRecording(alice, upload.recordingId(), wav);
        Path stored = mediaRoot.resolve("shadowing").resolve(alice.userId()).resolve(upload.recordingId() + ".wav");
        assertThat(Files.readAllBytes(stored)).isEqualTo(wav);

        ShadowingAttemptResponse result = service.submitAttempt(alice,
                new ShadowingAttemptRequest(lessonId, sentenceId, upload.recordingId(), null));
        assertThat(result.scorer()).isEqualTo("mock");
        assertThat(result.wordScores()).hasSize(4);
        assertThat(result.score()).isBetween(0.0, 100.0);
        assertThat(result.duration()).isEqualTo(2.0);
    }

    @Test
    void uploadRejectsNonWav() {
        String recordingId = UUID.randomUUID().toString();
        byte[] webm = new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 1, 2, 3, 4, 5, 6, 7, 8};
        assertThatThrownBy(() -> service.uploadLocalRecording(alice, recordingId, webm))
                .isInstanceOf(ApiException.class)
                .hasMessage(WavUtils.INVALID_FORMAT_MESSAGE);
        byte[] wav48k = WavUtils.encodePcm16(WavFixtures.tone(2.0, 48000, 0.5), 48000, 1);
        assertThatThrownBy(() -> service.uploadLocalRecording(alice, recordingId, wav48k))
                .hasMessage(WavUtils.INVALID_FORMAT_MESSAGE);
    }

    @Test
    void uploadRejectsPathTraversalAndOversize() {
        assertThatThrownBy(() -> service.uploadLocalRecording(alice, "../../etc/passwd", WavFixtures.toneWav(1)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        byte[] huge = new byte[ShadowingService.MAX_UPLOAD_BYTES + 1];
        assertThatThrownBy(() -> service.uploadLocalRecording(alice, UUID.randomUUID().toString(), huge))
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void submitWithoutUploadIs404AndCannotUseAnotherUsersRecording() {
        String recordingId = UUID.randomUUID().toString();
        service.uploadLocalRecording(alice, recordingId, WavFixtures.toneWav(1));
        // Bob guesses Alice's recordingId: keys are per-user, so it is simply not found for him.
        assertThatThrownBy(() -> service.submitAttempt(bob, new ShadowingAttemptRequest(lessonId, sentenceId, recordingId, null)))
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void recordingOfAnotherUserIs404() {
        UUID attemptId = UUID.randomUUID();
        when(repo.findByIdAndUserId(attemptId, bob.userUuid())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.streamRecording(bob, attemptId, new MockHttpServletResponse()))
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void ownerCanStreamRecording() throws Exception {
        String recordingId = UUID.randomUUID().toString();
        byte[] wav = WavFixtures.toneWav(1);
        service.uploadLocalRecording(alice, recordingId, wav);
        UUID attemptId = UUID.randomUUID();
        PracticeAttempt attempt = PracticeAttempt.builder().id(attemptId).userId(alice.userUuid())
                .audioRecordingUrl(RecordingStorage.keyFor(alice.userUuid(), recordingId)).build();
        when(repo.findByIdAndUserId(attemptId, alice.userUuid())).thenReturn(Optional.of(attempt));

        MockHttpServletResponse response = new MockHttpServletResponse();
        service.streamRecording(alice, attemptId, response);
        assertThat(response.getContentType()).isEqualTo("audio/wav");
        assertThat(response.getContentAsByteArray()).isEqualTo(wav);
    }

    @Test
    void s3ModeHasNoLocalUploadEndpoint() {
        RecordingStorage s3 = mock(RecordingStorage.class);
        when(s3.provider()).thenReturn("s3");
        ShadowingService s3Service = new ShadowingService(mock(ContentServiceClient.class), s3,
                new MockPronunciationScorer(), repo, new ObjectMapper(), mock(CacheService.class));
        assertThatThrownBy(() -> s3Service.uploadLocalRecording(alice, UUID.randomUUID().toString(), WavFixtures.toneWav(1)))
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
