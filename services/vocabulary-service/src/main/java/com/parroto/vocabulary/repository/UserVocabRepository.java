package com.parroto.vocabulary.repository;

import com.parroto.vocabulary.entity.UserVocab;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserVocabRepository extends JpaRepository<UserVocab, UUID> {

    Optional<UserVocab> findByUserIdAndCard_Id(UUID userId, UUID cardId);

    @Query("""
            SELECT uv.card.id, uv.id FROM UserVocab uv
            WHERE uv.userId = :userId AND uv.card.id IN :cardIds
            """)
    List<Object[]> findIdsByUserIdAndCardIdIn(@Param("userId") UUID userId, @Param("cardIds") Collection<UUID> cardIds);

    @Query("""
            SELECT uv FROM UserVocab uv JOIN FETCH uv.card
            WHERE uv.userId = :userId AND uv.nextReviewAt <= :now
            ORDER BY uv.nextReviewAt ASC
            """)
    List<UserVocab> findDueForReview(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

    /** Card and deck are fetched in the same query (LearnedWordResponse reads both: no N+1). */
    @Query(value = """
            SELECT uv FROM UserVocab uv JOIN FETCH uv.card c JOIN FETCH c.deck
            WHERE uv.userId = :userId
              AND (:searchWord IS NULL OR LOWER(c.word) LIKE LOWER(CONCAT('%', CAST(:searchWord AS string), '%')))
            ORDER BY uv.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(uv) FROM UserVocab uv JOIN uv.card c
            WHERE uv.userId = :userId
              AND (:searchWord IS NULL OR LOWER(c.word) LIKE LOWER(CONCAT('%', CAST(:searchWord AS string), '%')))
            """)
    Page<UserVocab> findLearned(@Param("userId") UUID userId, @Param("searchWord") String searchWord, Pageable pageable);

    @Query("SELECT uv FROM UserVocab uv JOIN FETCH uv.card c JOIN FETCH c.deck WHERE uv.id = :id")
    Optional<UserVocab> findWithCardAndDeck(@Param("id") UUID id);

    /** T13 duplicate check: {@code normalizedWord} is already {@code lower(trim(word))}. */
    @Query("""
            SELECT COUNT(uv) > 0 FROM UserVocab uv
            WHERE uv.userId = :userId AND LOWER(TRIM(uv.card.word)) = :normalizedWord
            """)
    boolean existsWord(@Param("userId") UUID userId, @Param("normalizedWord") String normalizedWord);

    @Query("""
            SELECT COUNT(uv) > 0 FROM UserVocab uv
            WHERE uv.userId = :userId AND LOWER(TRIM(uv.card.word)) = :normalizedWord AND uv.id <> :excludeId
            """)
    boolean existsWordExcluding(@Param("userId") UUID userId, @Param("normalizedWord") String normalizedWord,
                                @Param("excludeId") UUID excludeId);

    long countByUserId(UUID userId);

    long countByUserIdAndNextReviewAtLessThanEqual(UUID userId, OffsetDateTime cutoff);

    long countByUserIdAndRepetitions(UUID userId, Integer repetitions);

    @Query("SELECT COUNT(uv) FROM UserVocab uv WHERE uv.userId = :userId AND uv.repetitions BETWEEN 1 AND 3")
    long countLearning(@Param("userId") UUID userId);

    @Query("SELECT COUNT(uv) FROM UserVocab uv WHERE uv.userId = :userId AND uv.repetitions > 3")
    long countReview(@Param("userId") UUID userId);

    @Query("SELECT AVG(CASE WHEN uv.quality >= 3 THEN 1.0 ELSE 0.0 END) FROM UserVocab uv WHERE uv.userId = :userId AND uv.quality IS NOT NULL")
    Double averageAccuracy(@Param("userId") UUID userId);

    // --- T15 ---

    /** Notebook cards for a game round: due ones first, then the rest, random within each group. */
    @Query(value = """
            SELECT uv.card_id FROM user_vocab uv JOIN vocab_card c ON c.id = uv.card_id
            WHERE uv.user_id = :userId AND btrim(c.meaning) <> ''
            ORDER BY (uv.next_review_at <= now()) DESC, random() LIMIT :limit
            """, nativeQuery = true)
    List<UUID> notebookCardIdsForGame(@Param("userId") UUID userId, @Param("limit") int limit);

    List<UserVocab> findByUserIdAndCard_IdIn(UUID userId, Collection<UUID> cardIds);
}
