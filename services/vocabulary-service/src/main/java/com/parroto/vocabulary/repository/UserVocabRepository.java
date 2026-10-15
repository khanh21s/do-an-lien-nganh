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

    /**
     * Legacy {@code GET /vocabs/user/review-queue}: learning cards due before {@code learnUntil} and
     * review cards due before {@code dayCutoff} (start of the next Anki day), earliest first.
     */
    @Query("""
            SELECT uv FROM UserVocab uv JOIN FETCH uv.card
            WHERE uv.userId = :userId
              AND ((uv.state IN (com.parroto.vocabulary.entity.CardState.LEARNING, com.parroto.vocabulary.entity.CardState.RELEARNING)
                    AND uv.nextReviewAt <= :learnUntil)
                OR (uv.state = com.parroto.vocabulary.entity.CardState.REVIEW AND uv.nextReviewAt < :dayCutoff))
            ORDER BY uv.nextReviewAt ASC
            """)
    List<UserVocab> findDueForReview(@Param("userId") UUID userId, @Param("learnUntil") OffsetDateTime learnUntil,
                                     @Param("dayCutoff") OffsetDateTime dayCutoff);

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

    /** Cards already in learning/review (not NEW) that are due before {@code cutoff}. */
    @Query("""
            SELECT COUNT(uv) FROM UserVocab uv
            WHERE uv.userId = :userId AND uv.state <> com.parroto.vocabulary.entity.CardState.NEW
              AND uv.nextReviewAt < :cutoff
            """)
    long countScheduledDueBefore(@Param("userId") UUID userId, @Param("cutoff") OffsetDateTime cutoff);

    long countByUserIdAndState(UUID userId, com.parroto.vocabulary.entity.CardState state);

    @Query("""
            SELECT COUNT(uv) FROM UserVocab uv WHERE uv.userId = :userId
              AND uv.state IN (com.parroto.vocabulary.entity.CardState.LEARNING, com.parroto.vocabulary.entity.CardState.RELEARNING)
            """)
    long countLearning(@Param("userId") UUID userId);

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

    // --- P2-anki: study sessions (deck scope: cards of one deck, optional group; global scope: all rows) ---

    /** Group filter of the deck scope: mode 0 = whole deck, 1 = {@code groupId}, 2 = ungrouped cards. */
    String DECK_GROUP = """
            (:groupMode = 0 OR (:groupMode = 1 AND c.group_id = :groupId) OR (:groupMode = 2 AND c.group_id IS NULL))
            """;

    /** One row: [learning due before learnUntil, review due before dayCutoff, new]. */
    @Query(value = """
            SELECT
              count(uv.id) FILTER (WHERE uv.state IN ('LEARNING', 'RELEARNING') AND uv.next_review_at <= :learnUntil),
              count(uv.id) FILTER (WHERE uv.state = 'REVIEW' AND uv.next_review_at < :dayCutoff),
              count(*) FILTER (WHERE uv.id IS NULL OR uv.state = 'NEW')
            FROM vocab_card c
            LEFT JOIN user_vocab uv ON uv.card_id = c.id AND uv.user_id = :userId
            WHERE c.deck_id = :deckId AND """ + DECK_GROUP, nativeQuery = true)
    List<Object[]> deckStudyCounts(@Param("userId") UUID userId, @Param("deckId") UUID deckId,
                                   @Param("groupMode") int groupMode, @Param("groupId") UUID groupId,
                                   @Param("learnUntil") OffsetDateTime learnUntil,
                                   @Param("dayCutoff") OffsetDateTime dayCutoff);

    @Query(value = """
            SELECT
              count(*) FILTER (WHERE uv.state IN ('LEARNING', 'RELEARNING') AND uv.next_review_at <= :learnUntil),
              count(*) FILTER (WHERE uv.state = 'REVIEW' AND uv.next_review_at < :dayCutoff),
              count(*) FILTER (WHERE uv.state = 'NEW')
            FROM user_vocab uv WHERE uv.user_id = :userId
            """, nativeQuery = true)
    List<Object[]> globalStudyCounts(@Param("userId") UUID userId, @Param("learnUntil") OffsetDateTime learnUntil,
                                     @Param("dayCutoff") OffsetDateTime dayCutoff);

    /** Learning/relearning cards of the deck due by {@code until}, earliest first (use a page of 1). */
    @Query("""
            SELECT uv FROM UserVocab uv JOIN FETCH uv.card c
            WHERE uv.userId = :userId AND c.deck.id = :deckId
              AND (:groupMode = 0 OR (:groupMode = 1 AND c.groupId = :groupId) OR (:groupMode = 2 AND c.groupId IS NULL))
              AND uv.state IN (com.parroto.vocabulary.entity.CardState.LEARNING, com.parroto.vocabulary.entity.CardState.RELEARNING)
              AND uv.nextReviewAt <= :until
            ORDER BY uv.nextReviewAt ASC, uv.id ASC
            """)
    List<UserVocab> deckLearningDue(@Param("userId") UUID userId, @Param("deckId") UUID deckId,
                                    @Param("groupMode") int groupMode, @Param("groupId") UUID groupId,
                                    @Param("until") OffsetDateTime until, Pageable pageable);

    @Query("""
            SELECT uv FROM UserVocab uv JOIN FETCH uv.card c
            WHERE uv.userId = :userId
              AND uv.state IN (com.parroto.vocabulary.entity.CardState.LEARNING, com.parroto.vocabulary.entity.CardState.RELEARNING)
              AND uv.nextReviewAt <= :until
            ORDER BY uv.nextReviewAt ASC, uv.id ASC
            """)
    List<UserVocab> globalLearningDue(@Param("userId") UUID userId, @Param("until") OffsetDateTime until,
                                      Pageable pageable);

    /** Next due review card of the deck: earliest due day first, random (per seed) within a day. */
    @Query(value = """
            SELECT uv.* FROM user_vocab uv JOIN vocab_card c ON c.id = uv.card_id
            WHERE uv.user_id = :userId AND c.deck_id = :deckId AND """ + DECK_GROUP + """
              AND uv.state = 'REVIEW' AND uv.next_review_at < :dayCutoff
            ORDER BY uv.next_review_at, md5(CAST(uv.id AS text) || :seed)
            LIMIT 1
            """, nativeQuery = true)
    List<UserVocab> deckReviewDue(@Param("userId") UUID userId, @Param("deckId") UUID deckId,
                                  @Param("groupMode") int groupMode, @Param("groupId") UUID groupId,
                                  @Param("dayCutoff") OffsetDateTime dayCutoff, @Param("seed") String seed);

    @Query(value = """
            SELECT uv.* FROM user_vocab uv
            WHERE uv.user_id = :userId AND uv.state = 'REVIEW' AND uv.next_review_at < :dayCutoff
            ORDER BY uv.next_review_at, md5(CAST(uv.id AS text) || :seed)
            LIMIT 1
            """, nativeQuery = true)
    List<UserVocab> globalReviewDue(@Param("userId") UUID userId, @Param("dayCutoff") OffsetDateTime dayCutoff,
                                    @Param("seed") String seed);

    /** A new card of the deck (never graded: no row, or a NEW row), in a random order fixed by {@code seed}. */
    @Query(value = """
            SELECT c.id FROM vocab_card c
            LEFT JOIN user_vocab uv ON uv.card_id = c.id AND uv.user_id = :userId
            WHERE c.deck_id = :deckId AND """ + DECK_GROUP + """
              AND (uv.id IS NULL OR uv.state = 'NEW')
            ORDER BY md5(CAST(c.id AS text) || :seed)
            LIMIT 1
            """, nativeQuery = true)
    List<UUID> deckNewCardIds(@Param("userId") UUID userId, @Param("deckId") UUID deckId,
                              @Param("groupMode") int groupMode, @Param("groupId") UUID groupId,
                              @Param("seed") String seed);

    /** Global scope: NEW rows in the order they were added (Anki's default "order added"). */
    @Query("""
            SELECT uv FROM UserVocab uv JOIN FETCH uv.card
            WHERE uv.userId = :userId AND uv.state = com.parroto.vocabulary.entity.CardState.NEW
            ORDER BY uv.createdAt ASC, uv.id ASC
            """)
    List<UserVocab> globalNew(@Param("userId") UUID userId, Pageable pageable);

    /** When the next learning/review card of the deck falls due (for "Đã xong hôm nay"). */
    @Query("""
            SELECT MIN(uv.nextReviewAt) FROM UserVocab uv JOIN uv.card c
            WHERE uv.userId = :userId AND c.deck.id = :deckId
              AND (:groupMode = 0 OR (:groupMode = 1 AND c.groupId = :groupId) OR (:groupMode = 2 AND c.groupId IS NULL))
              AND uv.state <> com.parroto.vocabulary.entity.CardState.NEW
            """)
    OffsetDateTime deckNextDue(@Param("userId") UUID userId, @Param("deckId") UUID deckId,
                               @Param("groupMode") int groupMode, @Param("groupId") UUID groupId);

    @Query("""
            SELECT MIN(uv.nextReviewAt) FROM UserVocab uv
            WHERE uv.userId = :userId AND uv.state <> com.parroto.vocabulary.entity.CardState.NEW
            """)
    OffsetDateTime globalNextDue(@Param("userId") UUID userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uv FROM UserVocab uv WHERE uv.id = :id")
    Optional<UserVocab> findByIdForUpdate(@Param("id") UUID id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uv FROM UserVocab uv WHERE uv.userId = :userId AND uv.card.id = :cardId")
    Optional<UserVocab> findByUserIdAndCardIdForUpdate(@Param("userId") UUID userId, @Param("cardId") UUID cardId);

    /** Creates the NEW row of a card graded for the first time; a concurrent insert wins silently. */
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
            INSERT INTO user_vocab (user_id, card_id, state, step, lapses, interval_days, ease_factor, repetitions, next_review_at)
            VALUES (:userId, :cardId, 'NEW', 0, 0, 0, 2.50, 0, :now)
            ON CONFLICT (user_id, card_id) DO NOTHING
            """, nativeQuery = true)
    int insertNewIfAbsent(@Param("userId") UUID userId, @Param("cardId") UUID cardId, @Param("now") OffsetDateTime now);
}
