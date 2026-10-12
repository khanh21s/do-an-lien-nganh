package com.parroto.practice.repository;

import com.parroto.practice.entity.PracticeAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface PracticeAttemptRepository extends JpaRepository<PracticeAttempt, UUID> {

    @Query("""
            SELECT a FROM PracticeAttempt a
            WHERE a.userId = :userId
              AND (:lessonId IS NULL OR a.lessonId = :lessonId)
              AND (:mode IS NULL OR a.mode = :mode)
            ORDER BY a.createdAt DESC
            """)
    Page<PracticeAttempt> search(@Param("userId") UUID userId, @Param("lessonId") UUID lessonId,
                                  @Param("mode") String mode, Pageable pageable);

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

    @Query("""
            SELECT CAST(a.createdAt AS date), AVG(a.accuracy) FROM PracticeAttempt a
            WHERE a.userId = :userId AND a.createdAt >= :since
            GROUP BY CAST(a.createdAt AS date)
            ORDER BY CAST(a.createdAt AS date) ASC
            """)
    List<Object[]> dailyAccuracySince(@Param("userId") UUID userId, @Param("since") OffsetDateTime since);

    List<PracticeAttempt> findTop1ByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("""
            SELECT a FROM PracticeAttempt a
            WHERE a.userId = :userId AND a.lessonId = :lessonId AND a.sentenceId = :sentenceId
            """)
    List<PracticeAttempt> findByUserLessonSentence(@Param("userId") UUID userId,
                                                     @Param("lessonId") UUID lessonId,
                                                     @Param("sentenceId") UUID sentenceId);

    @Query(value = "SELECT DISTINCT CAST(created_at AS date) FROM practice_attempt WHERE user_id = :userId ORDER BY 1 DESC",
            nativeQuery = true)
    List<java.sql.Date> distinctPracticeDatesDesc(@Param("userId") UUID userId);
}
