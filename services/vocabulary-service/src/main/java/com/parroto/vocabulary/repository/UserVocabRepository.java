package com.parroto.vocabulary.repository;

import com.parroto.vocabulary.entity.UserVocab;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserVocabRepository extends JpaRepository<UserVocab, UUID> {

    Optional<UserVocab> findByUserIdAndCard_Id(UUID userId, UUID cardId);

    @Query("""
            SELECT uv FROM UserVocab uv
            WHERE uv.userId = :userId AND uv.nextReviewAt <= :now
            ORDER BY uv.nextReviewAt ASC
            """)
    List<UserVocab> findDueForReview(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

    @Query("""
            SELECT uv FROM UserVocab uv
            WHERE uv.userId = :userId
              AND (:searchWord IS NULL OR LOWER(uv.card.word) LIKE LOWER(CONCAT('%', CAST(:searchWord AS string), '%')))
            ORDER BY uv.createdAt DESC
            """)
    Page<UserVocab> findLearned(@Param("userId") UUID userId, @Param("searchWord") String searchWord, Pageable pageable);

    long countByUserId(UUID userId);

    long countByUserIdAndNextReviewAtLessThanEqual(UUID userId, OffsetDateTime cutoff);

    long countByUserIdAndRepetitions(UUID userId, Integer repetitions);

    @Query("SELECT COUNT(uv) FROM UserVocab uv WHERE uv.userId = :userId AND uv.repetitions BETWEEN 1 AND 3")
    long countLearning(@Param("userId") UUID userId);

    @Query("SELECT COUNT(uv) FROM UserVocab uv WHERE uv.userId = :userId AND uv.repetitions > 3")
    long countReview(@Param("userId") UUID userId);

    @Query("SELECT AVG(CASE WHEN uv.quality >= 3 THEN 1.0 ELSE 0.0 END) FROM UserVocab uv WHERE uv.userId = :userId AND uv.quality IS NOT NULL")
    Double averageAccuracy(@Param("userId") UUID userId);
}
