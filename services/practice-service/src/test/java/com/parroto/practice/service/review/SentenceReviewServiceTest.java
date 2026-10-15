package com.parroto.practice.service.review;

import com.parroto.common.exception.ApiException;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.practice.dto.SentenceReviewItemResponse;
import com.parroto.practice.dto.SentenceReviewOutcome;
import com.parroto.practice.entity.SentenceReview;
import com.parroto.practice.repository.SentenceReviewRepository;
import com.parroto.practice.service.ContentServiceClient;
import com.parroto.practice.service.LessonAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** P2-A2: queue rule, review grading with SM-2, queue enrichment. */
class SentenceReviewServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 15, 3, 0, 0, 0, ZoneOffset.UTC);

    private final UUID userId = UUID.randomUUID();
    private final AuthenticatedUser user = new AuthenticatedUser(userId.toString(), "u@x.vn", "B1");
    private final UUID lessonId = UUID.randomUUID();
    private final UUID sentenceId = UUID.randomUUID();

    private SentenceReviewRepository repo;
    private ContentServiceClient content;
    private LessonAccessGuard guard;
    private SentenceReviewService service;

    @BeforeEach
    void setUp() {
        repo = mock(SentenceReviewRepository.class);
        content = mock(ContentServiceClient.class);
        guard = mock(LessonAccessGuard.class);
        service = new SentenceReviewService(repo, content, guard, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private SentenceReview item(String mode, int reps, int interval, String ef, OffsetDateTime due) {
        return SentenceReview.builder().id(UUID.randomUUID()).userId(userId).lessonId(lessonId).sentenceId(sentenceId)
                .mode(mode).repetitions(reps).intervalDays(interval).easeFactor(new BigDecimal(ef))
                .nextReviewAt(due).createdAt(NOW.minusDays(3)).updatedAt(NOW.minusDays(3)).build();
    }

    @Test
    void failedDictationIsQueuedDueNow() {
        SentenceReview queued = item("dictation", 0, 0, "2.50", NOW);
        when(repo.findByUserIdAndSentenceIdAndMode(userId, sentenceId, "dictation")).thenReturn(Optional.of(queued));

        SentenceReviewOutcome out = service.recordAttempt(userId, lessonId, sentenceId, "dictation", 75.0, null);

        verify(repo).upsertFailed(userId, sentenceId, lessonId, "dictation", NOW, new BigDecimal("75.00"), NOW);
        assertThat(out.action()).isEqualTo("queued");
        assertThat(out.nextReviewAt()).isEqualTo(NOW);
        assertThat(out.quality()).isNull();
    }

    @Test
    void failedShadowingBelowSeventyIsQueued() {
        service.recordAttempt(userId, lessonId, sentenceId, "shadowing", 69.0, null);
        verify(repo).upsertFailed(eq(userId), eq(sentenceId), eq(lessonId), eq("shadowing"), eq(NOW), any(), eq(NOW));
    }

    @Test
    void passingNormalAttemptDoesNotTouchTheQueue() {
        assertThat(service.recordAttempt(userId, lessonId, sentenceId, "dictation", 100.0, null)).isNull();
        assertThat(service.recordAttempt(userId, lessonId, sentenceId, "shadowing", 70.0, null)).isNull();

        SentenceReview existing = item("dictation", 2, 6, "2.50", NOW.minusHours(1));
        when(repo.findByUserIdAndSentenceIdAndMode(userId, sentenceId, "dictation")).thenReturn(Optional.of(existing));
        SentenceReviewOutcome out = service.recordAttempt(userId, lessonId, sentenceId, "dictation", 100.0, null);

        assertThat(out.action()).isEqualTo("unchanged");
        assertThat(existing.getRepetitions()).isEqualTo(2);
        verify(repo, never()).upsertFailed(any(), any(), any(), any(), any(), any(), any());
        verify(repo, never()).save(any());
    }

    @Test
    void reviewAttemptGradesDueItemWithSm2() {
        SentenceReview due = item("dictation", 1, 1, "2.50", NOW.minusMinutes(5));
        when(repo.lockByIdAndUserId(due.getId(), userId)).thenReturn(Optional.of(due));

        SentenceReviewOutcome out = service.recordAttempt(userId, lessonId, sentenceId, "dictation", 100.0, due.getId());

        assertThat(out.action()).isEqualTo("graded");
        assertThat(out.quality()).isEqualTo(4);
        assertThat(out.repetitions()).isEqualTo(2);
        assertThat(out.intervalDays()).isEqualTo(6);
        assertThat(out.easeFactor()).isEqualByComparingTo("2.50");
        assertThat(out.nextReviewAt()).isEqualTo(NOW.plusDays(6));
        assertThat(due.getLastReviewedAt()).isEqualTo(NOW);
        assertThat(due.getLastScore()).isEqualByComparingTo("100");
        verify(repo, never()).upsertFailed(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void hardDictationReviewUsesQualityThree() {
        SentenceReview due = item("dictation", 3, 10, "2.50", NOW);
        when(repo.lockByIdAndUserId(due.getId(), userId)).thenReturn(Optional.of(due));

        SentenceReviewOutcome out = service.recordAttempt(userId, lessonId, sentenceId, "dictation", 85.0, due.getId());

        assertThat(out.quality()).isEqualTo(3);
        assertThat(out.intervalDays()).isEqualTo(25);
        assertThat(out.easeFactor()).isEqualByComparingTo("2.36");
        assertThat(out.nextReviewAt()).isEqualTo(NOW.plusDays(25));
    }

    @Test
    void failedReviewIsSm2AgainTomorrowNotAResetToNow() {
        SentenceReview due = item("shadowing", 3, 10, "2.50", NOW.minusDays(1));
        when(repo.lockByIdAndUserId(due.getId(), userId)).thenReturn(Optional.of(due));

        SentenceReviewOutcome out = service.recordAttempt(userId, lessonId, sentenceId, "shadowing", 40.0, due.getId());

        assertThat(out.action()).isEqualTo("graded");
        assertThat(out.quality()).isEqualTo(1);
        assertThat(out.repetitions()).isZero();
        assertThat(out.intervalDays()).isEqualTo(1);
        assertThat(out.easeFactor()).isEqualByComparingTo("1.96");
        assertThat(out.nextReviewAt()).isEqualTo(NOW.plusDays(1));
        verify(repo, never()).upsertFailed(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void retryOnAlreadyGradedItemChangesNothing() {
        SentenceReview graded = item("dictation", 0, 1, "1.96", NOW.plusDays(1));
        when(repo.lockByIdAndUserId(graded.getId(), userId)).thenReturn(Optional.of(graded));

        SentenceReviewOutcome out = service.recordAttempt(userId, lessonId, sentenceId, "dictation", 30.0, graded.getId());

        assertThat(out.action()).isEqualTo("unchanged");
        assertThat(out.nextReviewAt()).isEqualTo(NOW.plusDays(1));
        verify(repo, never()).save(any());
        verify(repo, never()).upsertFailed(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void foreignOrMismatchingReviewIdFallsBackToTheNormalRule() {
        UUID foreign = UUID.randomUUID();
        when(repo.lockByIdAndUserId(foreign, userId)).thenReturn(Optional.empty());
        service.recordAttempt(userId, lessonId, sentenceId, "dictation", 50.0, foreign);
        verify(repo).upsertFailed(eq(userId), eq(sentenceId), eq(lessonId), eq("dictation"), eq(NOW), any(), eq(NOW));

        // same user but another mode's item → not graded
        SentenceReview shadowItem = item("shadowing", 0, 0, "2.50", NOW);
        when(repo.lockByIdAndUserId(shadowItem.getId(), userId)).thenReturn(Optional.of(shadowItem));
        service.recordAttempt(userId, lessonId, sentenceId, "dictation", 100.0, shadowItem.getId());
        verify(repo, never()).save(any());
    }

    @Test
    void queueEnrichesFlagsLockedAndPurgesOrphans() {
        UUID goneLesson = UUID.randomUUID();
        SentenceReview ok = item("dictation", 0, 0, "2.50", NOW);
        SentenceReview missingSentence = item("shadowing", 0, 0, "2.50", NOW);
        missingSentence.setSentenceId(UUID.randomUUID());
        SentenceReview deletedLesson = item("dictation", 0, 0, "2.50", NOW);
        deletedLesson.setLessonId(goneLesson);
        when(repo.findDue(eq(userId), eq(NOW), any())).thenReturn(List.of(ok, missingSentence, deletedLesson));
        ContentServiceClient.SentenceRef s = new ContentServiceClient.SentenceRef(sentenceId, "Hello there.", null, "Xin chào",
                1, 0, 900, null, null);
        when(content.findLesson(lessonId)).thenReturn(Optional.of(
                new ContentServiceClient.LessonRef(lessonId, "Pro lesson", "local:seed/x.mp3", true, List.of(s))));
        when(content.findLesson(goneLesson)).thenReturn(Optional.empty());
        when(guard.canPractice(user, true)).thenReturn(false);

        List<SentenceReviewItemResponse> items = service.queue(user, null);

        assertThat(items).hasSize(1);
        SentenceReviewItemResponse first = items.get(0);
        assertThat(first.sentence().text()).isEqualTo("Hello there.");
        assertThat(first.lessonTitle()).isEqualTo("Pro lesson");
        assertThat(first.lessonIsPro()).isTrue();
        assertThat(first.locked()).isTrue();
        verify(content, times(1)).findLesson(lessonId); // one content call per lesson
        verify(repo).deleteAll(List.of(missingSentence, deletedLesson));
    }

    @Test
    void queueLimitIsValidated() {
        assertThatThrownBy(() -> service.queue(user, 0)).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.queue(user, 101)).isInstanceOf(ApiException.class);
        verify(guard, never()).canPractice(any(), anyBoolean());
    }

    @Test
    void removeOnlyOwnItems() {
        UUID id = UUID.randomUUID();
        when(repo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.remove(user, id)).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(repo, never()).delete(any());
    }
}
