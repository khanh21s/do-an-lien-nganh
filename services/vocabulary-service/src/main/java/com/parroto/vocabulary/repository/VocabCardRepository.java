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
}
