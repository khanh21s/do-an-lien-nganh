package com.parroto.practice.repository;

import com.parroto.practice.entity.PracticeAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PracticeAttemptRepository extends JpaRepository<PracticeAttempt, UUID> {

    /**
     * Attempts of one user, newest first, optionally filtered by lesson and/or mode.
     * Dispatches to derived queries instead of {@code (:param IS NULL OR ...)}, which PostgreSQL
     * rejects for null UUID parameters ("could not determine data type of parameter").
     */
    default Page<PracticeAttempt> search(UUID userId, UUID lessonId, String mode, Pageable pageable) {
        boolean hasMode = mode != null && !mode.isBlank();
        if (lessonId != null && hasMode) {
            return findByUserIdAndLessonIdAndModeOrderByCreatedAtDesc(userId, lessonId, mode, pageable);
        }
        if (lessonId != null) {
            return findPageByUserIdAndLessonIdOrderByCreatedAtDesc(userId, lessonId, pageable);
        }
        if (hasMode) {
            return findByUserIdAndModeOrderByCreatedAtDesc(userId, mode, pageable);
        }
        return findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    Page<PracticeAttempt> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<PracticeAttempt> findPageByUserIdAndLessonIdOrderByCreatedAtDesc(UUID userId, UUID lessonId, Pageable pageable);

    Page<PracticeAttempt> findByUserIdAndModeOrderByCreatedAtDesc(UUID userId, String mode, Pageable pageable);

    Page<PracticeAttempt> findByUserIdAndLessonIdAndModeOrderByCreatedAtDesc(UUID userId, UUID lessonId, String mode,
                                                                             Pageable pageable);

    List<PracticeAttempt> findByUserIdAndLessonIdOrderByCreatedAtDesc(UUID userId, UUID lessonId);

    long countByUserId(UUID userId);

    @Query("SELECT AVG(a.accuracy) FROM PracticeAttempt a WHERE a.userId = :userId AND a.mode = :mode")
    Double averageAccuracy(@Param("userId") UUID userId, @Param("mode") String mode);

    @Query("SELECT AVG(a.score) FROM PracticeAttempt a WHERE a.userId = :userId")
    Double averageScore(@Param("userId") UUID userId);

    @Query("SELECT COUNT(DISTINCT a.lessonId) FROM PracticeAttempt a WHERE a.userId = :userId")
    long countDistinctLessonsCompleted(@Param("userId") UUID userId);

    @Query("SELECT SUM(a.duration) FROM PracticeAttempt a WHERE a.userId = :userId")
    Double sumDuration(@Param("userId") UUID userId);

    /**
     * Daily average accuracy since {@code since}, bucketed by Vietnam calendar day.
     * Rows: [day 'YYYY-MM-DD' (String), avg accuracy (Number)].
     */
    @Query(value = """
            SELECT to_char(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM-DD') AS day, AVG(accuracy) AS avg_accuracy
            FROM practice_attempt
            WHERE user_id = :userId AND created_at >= :since
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> dailyAccuracySince(@Param("userId") UUID userId, @Param("since") OffsetDateTime since);

    /**
     * PRA-03 heatmap source: per Vietnam calendar day, attempt count and total duration (seconds).
     * Rows: [day 'YYYY-MM-DD' (String), attempts (Number), duration seconds (Number)].
     */
    @Query(value = """
            SELECT to_char(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM-DD') AS day,
                   COUNT(*) AS attempts,
                   COALESCE(SUM(duration), 0) AS seconds
            FROM practice_attempt
            WHERE user_id = :userId AND created_at >= :since
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> dailyActivitySince(@Param("userId") UUID userId, @Param("since") OffsetDateTime since);

    List<PracticeAttempt> findTop1ByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("""
            SELECT a FROM PracticeAttempt a
            WHERE a.userId = :userId AND a.lessonId = :lessonId AND a.sentenceId = :sentenceId
            """)
    List<PracticeAttempt> findByUserLessonSentence(@Param("userId") UUID userId,
                                                     @Param("lessonId") UUID lessonId,
                                                     @Param("sentenceId") UUID sentenceId);

    /** PRA-07: distinct practice days ('YYYY-MM-DD', Vietnam time), most recent first. */
    @Query(value = """
            SELECT DISTINCT to_char(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM-DD') AS day
            FROM practice_attempt
            WHERE user_id = :userId
            ORDER BY 1 DESC
            """, nativeQuery = true)
    List<String> distinctPracticeDatesDesc(@Param("userId") UUID userId);

    /** SHA-04: ownership-scoped lookup; a foreign attempt is indistinguishable from a missing one. */
    Optional<PracticeAttempt> findByIdAndUserId(UUID id, UUID userId);
}
