package com.parroto.practice.service.review;

import com.parroto.common.exception.ApiException;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.practice.dto.SentenceReviewItemResponse;
import com.parroto.practice.dto.SentenceReviewOutcome;
import com.parroto.practice.dto.SentenceReviewStatsResponse;
import com.parroto.practice.entity.SentenceReview;
import com.parroto.practice.repository.SentenceReviewRepository;
import com.parroto.practice.service.ContentServiceClient;
import com.parroto.practice.service.LessonAccessGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * P2-A2 sentence review queue ("Ôn câu sai").
 *
 * <p>Every dictation/shadowing attempt goes through {@link #recordAttempt}:
 * <ol>
 *   <li>Attempt sent from the review screen ({@code reviewId} of the caller's own item for the same
 *       sentence + mode): if the item is due it is graded with SM-2 ({@link ReviewRules#quality}) — a
 *       failed review is SM-2 "Again" (due in 1 day), not a reset to "now". If it is not due (already
 *       graded, e.g. a retry right after the graded try) nothing changes.</li>
 *   <li>Any other attempt that fails {@link ReviewRules#fails}: the sentence is added, or reset
 *       (repetitions 0, interval 0, ease factor kept) and due <b>now</b>, so it shows up in today's queue.</li>
 *   <li>Any other passing attempt does not touch the queue (it is not counted as a review: in normal
 *       practice the learner just saw the corrections, so a pass there says little about recall).</li>
 * </ol>
 */
@Service
public class SentenceReviewService {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    private final SentenceReviewRepository repository;
    private final ContentServiceClient contentServiceClient;
    private final LessonAccessGuard accessGuard;
    private final Clock clock;

    @Autowired
    public SentenceReviewService(SentenceReviewRepository repository, ContentServiceClient contentServiceClient,
                                 LessonAccessGuard accessGuard) {
        this(repository, contentServiceClient, accessGuard, Clock.systemUTC());
    }

    public SentenceReviewService(SentenceReviewRepository repository, ContentServiceClient contentServiceClient,
                                 LessonAccessGuard accessGuard, Clock clock) {
        this.repository = repository;
        this.contentServiceClient = contentServiceClient;
        this.accessGuard = accessGuard;
        this.clock = clock;
    }

    /**
     * @param result   Dictation accuracy or Shadowing score, 0–100
     * @param reviewId optional, sent by the review screen
     * @return the effect on the queue, or null when the sentence is not (and does not get) queued
     */
    @Transactional
    public SentenceReviewOutcome recordAttempt(UUID userId, UUID lessonId, UUID sentenceId, String mode,
                                               double result, UUID reviewId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (reviewId != null) {
            Optional<SentenceReview> item = repository.lockByIdAndUserId(reviewId, userId)
                    .filter(r -> sentenceId.equals(r.getSentenceId()) && mode.equals(r.getMode()));
            if (item.isPresent()) {
                SentenceReview r = item.get();
                if (r.getNextReviewAt().isAfter(now)) {
                    return SentenceReviewOutcome.of(r, SentenceReviewOutcome.UNCHANGED, null);
                }
                int quality = ReviewRules.quality(mode, result);
                applyGrade(r, quality, result, now);
                return SentenceReviewOutcome.of(repository.save(r), SentenceReviewOutcome.GRADED, quality);
            }
            // Stale / foreign / mismatching reviewId: treat as a normal attempt.
        }
        if (ReviewRules.fails(mode, result)) {
            repository.upsertFailed(userId, sentenceId, lessonId, mode, now, score(result), now);
            return repository.findByUserIdAndSentenceIdAndMode(userId, sentenceId, mode)
                    .map(r -> SentenceReviewOutcome.of(r, SentenceReviewOutcome.QUEUED, null))
                    .orElse(null);
        }
        return repository.findByUserIdAndSentenceIdAndMode(userId, sentenceId, mode)
                .map(r -> SentenceReviewOutcome.of(r, SentenceReviewOutcome.UNCHANGED, null))
                .orElse(null);
    }

    /** SM-2 step (architecture §4.6) on a due item; next review = now + interval days. */
    static void applyGrade(SentenceReview r, int quality, double result, OffsetDateTime now) {
        SentenceSm2.Result sm2 = SentenceSm2.calculate(r.getRepetitions(), r.getIntervalDays(), r.getEaseFactor(), quality);
        r.setRepetitions(sm2.repetitions());
        r.setIntervalDays(sm2.interval());
        r.setEaseFactor(sm2.easeFactor());
        r.setNextReviewAt(now.plusDays(sm2.interval()));
        r.setLastScore(score(result));
        r.setLastReviewedAt(now);
        r.setUpdatedAt(now);
    }

    /**
     * Due items (oldest due first) with sentence/lesson data from content-service. Items whose lesson
     * or sentence no longer exists (content-service 404) are removed from the queue.
     */
    @Transactional
    public List<SentenceReviewItemResponse> queue(AuthenticatedUser user, Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw ApiException.badRequest("limit phải từ 1 đến " + MAX_LIMIT);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<SentenceReview> due = repository.findDue(user.userUuid(), now, PageRequest.of(0, size));

        Map<UUID, Optional<ContentServiceClient.LessonRef>> lessons = new HashMap<>();
        Map<Boolean, Boolean> canPractice = new HashMap<>();
        List<SentenceReviewItemResponse> items = new ArrayList<>();
        List<SentenceReview> orphans = new ArrayList<>();
        for (SentenceReview r : due) {
            Optional<ContentServiceClient.LessonRef> lesson =
                    lessons.computeIfAbsent(r.getLessonId(), contentServiceClient::findLesson);
            Optional<ContentServiceClient.SentenceRef> sentence = lesson.flatMap(l -> l.sentence(r.getSentenceId()));
            if (sentence.isEmpty()) {
                orphans.add(r);
                continue;
            }
            boolean isPro = lesson.get().isPro();
            boolean allowed = canPractice.computeIfAbsent(isPro, pro -> accessGuard.canPractice(user, pro));
            items.add(SentenceReviewItemResponse.from(r, lesson.get(), sentence.get(), !allowed));
        }
        if (!orphans.isEmpty()) {
            repository.deleteAll(orphans);
        }
        return items;
    }

    @Transactional(readOnly = true)
    public SentenceReviewStatsResponse stats(AuthenticatedUser user) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        UUID userId = user.userUuid();
        return new SentenceReviewStatsResponse(repository.countDue(userId, now), repository.countByUserId(userId),
                repository.nextDueAfter(userId, now));
    }

    /** Only the owner can remove an item; anything else is 404 (architecture §5.1). */
    @Transactional
    public void remove(AuthenticatedUser user, UUID id) {
        SentenceReview r = repository.findByIdAndUserId(id, user.userUuid())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy câu trong hàng ôn tập"));
        repository.delete(r);
    }

    private static BigDecimal score(double result) {
        double clamped = Double.isNaN(result) ? 0.0 : Math.max(0.0, Math.min(100.0, result));
        return BigDecimal.valueOf(clamped).setScale(2, RoundingMode.HALF_UP);
    }
}
