package com.parroto.vocabulary.repository;

import com.parroto.vocabulary.entity.VocabCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface VocabCardRepository extends JpaRepository<VocabCard, UUID> {

    @Query("""
            SELECT c FROM VocabCard c
            WHERE c.deck.id = :deckId
              AND (:searchWord IS NULL OR LOWER(c.word) LIKE LOWER(CONCAT('%', CAST(:searchWord AS string), '%')))
            ORDER BY c.word ASC
            """)
    Page<VocabCard> findByDeck(@Param("deckId") UUID deckId, @Param("searchWord") String searchWord, Pageable pageable);

    /** Deck cards in a pseudo-random order fixed by {@code seed}, so pages of one visit don't overlap. */
    @Query(value = """
            SELECT c.* FROM vocab_card c WHERE c.deck_id = :deckId
            ORDER BY md5(c.id::text || :seed)
            """,
            countQuery = "SELECT count(*) FROM vocab_card c WHERE c.deck_id = :deckId",
            nativeQuery = true)
    Page<VocabCard> findByDeckShuffled(@Param("deckId") UUID deckId, @Param("seed") String seed, Pageable pageable);

    long countByDeck_Id(UUID deckId);

    boolean existsByDeck_IdAndWordIgnoreCase(UUID deckId, String word);

    // --- T15: game question pools (random order, cards without a meaning are useless here) ---

    @Query(value = """
            SELECT c.id FROM vocab_card c
            WHERE c.deck_id = :deckId AND btrim(c.meaning) <> ''
            ORDER BY random() LIMIT :limit
            """, nativeQuery = true)
    List<UUID> randomIdsInDeck(@Param("deckId") UUID deckId, @Param("limit") int limit);

    /** Public decks of a CEFR level; Pro decks only when {@code includePro}. */
    @Query(value = """
            SELECT c.id FROM vocab_card c JOIN vocab_deck d ON d.id = c.deck_id
            WHERE d.owner_id IS NULL AND d.level = :level AND btrim(c.meaning) <> ''
              AND (:includePro OR COALESCE(d.is_pro_only, false) = false)
            ORDER BY random() LIMIT :limit
            """, nativeQuery = true)
    List<UUID> randomIdsByLevel(@Param("level") String level, @Param("includePro") boolean includePro,
                                @Param("limit") int limit);

    @Query("SELECT c FROM VocabCard c JOIN FETCH c.deck WHERE c.id = :id")
    java.util.Optional<VocabCard> findWithDeck(@Param("id") UUID id);

    /** Cards spelled like the typed answer ({@code words} are lower-cased) that the user can see. */
    @Query("""
            SELECT c FROM VocabCard c JOIN c.deck d
            WHERE LOWER(TRIM(c.word)) IN :words AND (d.ownerId IS NULL OR d.ownerId = :userId)
            """)
    List<VocabCard> findSameWordVisibleTo(@Param("words") Collection<String> words, @Param("userId") UUID userId);

    /** T16: public decks of any of {@code levels} (Survival stages, Daily challenge). */
    @Query(value = """
            SELECT c.id FROM vocab_card c JOIN vocab_deck d ON d.id = c.deck_id
            WHERE d.owner_id IS NULL AND d.level IN (:levels) AND btrim(c.meaning) <> ''
              AND (:includePro OR COALESCE(d.is_pro_only, false) = false)
            ORDER BY random() LIMIT :limit
            """, nativeQuery = true)
    List<UUID> randomIdsByLevels(@Param("levels") Collection<String> levels, @Param("includePro") boolean includePro,
                                 @Param("limit") int limit);

    /** T16: any public card (fallback when the level stages do not have enough words). */
    @Query(value = """
            SELECT c.id FROM vocab_card c JOIN vocab_deck d ON d.id = c.deck_id
            WHERE d.owner_id IS NULL AND btrim(c.meaning) <> ''
              AND (:includePro OR COALESCE(d.is_pro_only, false) = false)
            ORDER BY random() LIMIT :limit
            """, nativeQuery = true)
    List<UUID> randomPublicIds(@Param("includePro") boolean includePro, @Param("limit") int limit);

    // --- P2-C: groups, limits, duplicate checks, dictionary ---

    @Query("""
            SELECT c FROM VocabCard c
            WHERE c.deck.id = :deckId AND c.groupId = :groupId
              AND (:searchWord IS NULL OR LOWER(c.word) LIKE LOWER(CONCAT('%', CAST(:searchWord AS string), '%')))
            ORDER BY c.word ASC
            """)
    Page<VocabCard> findByDeckAndGroup(@Param("deckId") UUID deckId, @Param("groupId") UUID groupId,
                                       @Param("searchWord") String searchWord, Pageable pageable);

    @Query("""
            SELECT c FROM VocabCard c
            WHERE c.deck.id = :deckId AND c.groupId IS NULL
              AND (:searchWord IS NULL OR LOWER(c.word) LIKE LOWER(CONCAT('%', CAST(:searchWord AS string), '%')))
            ORDER BY c.word ASC
            """)
    Page<VocabCard> findByDeckUngrouped(@Param("deckId") UUID deckId, @Param("searchWord") String searchWord,
                                        Pageable pageable);

    @Query(value = """
            SELECT c.* FROM vocab_card c WHERE c.deck_id = :deckId AND c.group_id = :groupId
            ORDER BY md5(c.id::text || :seed)
            """,
            countQuery = "SELECT count(*) FROM vocab_card c WHERE c.deck_id = :deckId AND c.group_id = :groupId",
            nativeQuery = true)
    Page<VocabCard> findByDeckAndGroupShuffled(@Param("deckId") UUID deckId, @Param("groupId") UUID groupId,
                                               @Param("seed") String seed, Pageable pageable);

    @Query(value = """
            SELECT c.* FROM vocab_card c WHERE c.deck_id = :deckId AND c.group_id IS NULL
            ORDER BY md5(c.id::text || :seed)
            """,
            countQuery = "SELECT count(*) FROM vocab_card c WHERE c.deck_id = :deckId AND c.group_id IS NULL",
            nativeQuery = true)
    Page<VocabCard> findByDeckUngroupedShuffled(@Param("deckId") UUID deckId, @Param("seed") String seed,
                                                Pageable pageable);

    /** groupId (NULL = ungrouped) -> card count of one deck. */
    @Query("SELECT c.groupId, COUNT(c) FROM VocabCard c WHERE c.deck.id = :deckId GROUP BY c.groupId")
    List<Object[]> countByGroup(@Param("deckId") UUID deckId);

    /** Cards in the user's personal decks other than the notebook (the P2-C card limit). */
    @Query("SELECT COUNT(c) FROM VocabCard c WHERE c.deck.ownerId = :ownerId AND c.deck.isNotebook = false")
    long countCustomCards(@Param("ownerId") UUID ownerId);

    /** {@code lower(trim(word))} of every card of a deck (duplicate detection for import/add). */
    @Query("SELECT LOWER(TRIM(c.word)) FROM VocabCard c WHERE c.deck.id = :deckId")
    List<String> normalizedWordsInDeck(@Param("deckId") UUID deckId);

    /** {@code lower(trim(word))} of every word in the user's notebook (= any card with a user_vocab row). */
    @Query("SELECT LOWER(TRIM(c.word)) FROM UserVocab uv JOIN uv.card c WHERE uv.userId = :userId")
    List<String> normalizedNotebookWords(@Param("userId") UUID userId);

    @Query("""
            SELECT COUNT(c) > 0 FROM VocabCard c
            WHERE c.deck.id = :deckId AND LOWER(TRIM(c.word)) = :normalizedWord AND c.id <> :excludeId
            """)
    boolean existsWordInDeckExcluding(@Param("deckId") UUID deckId, @Param("normalizedWord") String normalizedWord,
                                      @Param("excludeId") UUID excludeId);

    @Query("SELECT c FROM VocabCard c WHERE c.deck.id = :deckId AND c.id IN :ids")
    List<VocabCard> findInDeck(@Param("deckId") UUID deckId, @Param("ids") Collection<UUID> ids);

    /**
     * Dictionary: cards spelled like one of {@code words} (lower-cased) in free public decks or in
     * the caller's own decks (guests pass a UUID that owns nothing). Own cards come first.
     */
    @Query("""
            SELECT c FROM VocabCard c JOIN FETCH c.deck d
            WHERE LOWER(TRIM(c.word)) IN :words
              AND ((d.ownerId IS NULL AND COALESCE(d.isProOnly, false) = false)
                   OR d.ownerId = :userId)
            ORDER BY CASE WHEN d.ownerId IS NULL THEN 1 ELSE 0 END, c.createdAt ASC
            """)
    List<VocabCard> findForDictionary(@Param("words") Collection<String> words, @Param("userId") UUID userId,
                                      Pageable pageable);
}
