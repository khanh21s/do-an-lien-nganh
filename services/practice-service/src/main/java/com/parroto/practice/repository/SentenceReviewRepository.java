package com.parroto.practice.repository;

import com.parroto.practice.entity.SentenceReview;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SentenceReviewRepository extends JpaRepository<SentenceReview, UUID> {

    /**
     * P2-A2 failed attempt: adds the sentence to the queue, or resets an existing entry
     * (repetitions 0, interval 0, due {@code dueAt}; ease factor is kept). Atomic, so two concurrent
     * failed attempts cannot violate the (user, sentence, mode) unique key.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO sentence_review (user_id, sentence_id, lesson_id, mode, repetitions, interval_days,
                                         ease_factor, next_review_at, last_score, updated_at)
            VALUES (:userId, :sentenceId, :lessonId, :mode, 0, 0, 2.50, :dueAt, :score, :now)
            ON CONFLICT (user_id, sentence_id, mode) DO UPDATE
               SET repetitions = 0, interval_days = 0, next_review_at = EXCLUDED.next_review_at,
                   last_score = EXCLUDED.last_score, lesson_id = EXCLUDED.lesson_id, updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    int upsertFailed(@Param("userId") UUID userId, @Param("sentenceId") UUID sentenceId,
                     @Param("lessonId") UUID lessonId, @Param("mode") String mode,
                     @Param("dueAt") OffsetDateTime dueAt, @Param("score") BigDecimal score,
                     @Param("now") OffsetDateTime now);

    Optional<SentenceReview> findByUserIdAndSentenceIdAndMode(UUID userId, UUID sentenceId, String mode);

    /** Row lock while grading so two concurrent review submits are applied one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SentenceReview r WHERE r.id = :id AND r.userId = :userId")
    Optional<SentenceReview> lockByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    Optional<SentenceReview> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            SELECT r FROM SentenceReview r
            WHERE r.userId = :userId AND r.nextReviewAt <= :now
            ORDER BY r.nextReviewAt ASC, r.createdAt ASC
            """)
    List<SentenceReview> findDue(@Param("userId") UUID userId, @Param("now") OffsetDateTime now, Pageable pageable);

    @Query("SELECT COUNT(r) FROM SentenceReview r WHERE r.userId = :userId AND r.nextReviewAt <= :now")
    long countDue(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

    long countByUserId(UUID userId);

    @Query("SELECT MIN(r.nextReviewAt) FROM SentenceReview r WHERE r.userId = :userId AND r.nextReviewAt > :now")
    OffsetDateTime nextDueAfter(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);
}
