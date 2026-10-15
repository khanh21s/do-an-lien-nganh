package com.parroto.vocabulary.repository;

import com.parroto.vocabulary.entity.VocabDeck;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VocabDeckRepository extends JpaRepository<VocabDeck, UUID> {

    /** Public decks only: personal decks (owner_id set) are never listed (T13). */
    @Query("""
            SELECT d FROM VocabDeck d
            WHERE d.ownerId IS NULL
              AND (:level IS NULL OR d.level = :level)
              AND (:isProOnly IS NULL OR d.isProOnly = :isProOnly)
            ORDER BY d.createdAt DESC
            """)
    Page<VocabDeck> search(@Param("level") String level, @Param("isProOnly") Boolean isProOnly, Pageable pageable);

    /**
     * The user's notebook deck (P2-C: {@code is_notebook}; other personal decks are excluded), row-locked for the rest of the transaction so concurrent
     * adds/edits by the same user (duplicate check, 2000-word limit, card_count) serialize.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM VocabDeck d WHERE d.ownerId = :ownerId AND d.isNotebook = true")
    Optional<VocabDeck> findByOwnerIdForUpdate(@Param("ownerId") UUID ownerId);

    /** Lazily creates the notebook deck; a concurrent creator wins silently (unique ux_vocab_deck_notebook, V3). */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO vocab_deck (name, is_pro_only, card_count, owner_id, is_notebook)
            VALUES (:name, false, 0, :ownerId, true)
            ON CONFLICT (owner_id) WHERE is_notebook DO NOTHING
            """, nativeQuery = true)
    int insertPersonalDeckIfAbsent(@Param("ownerId") UUID ownerId, @Param("name") String name);

    // --- P2-C: personal decks + groups ---

    /** Any deck row-locked (serializes card-count limits and duplicate checks of one deck). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM VocabDeck d WHERE d.id = :id")
    Optional<VocabDeck> findByIdForUpdate(@Param("id") UUID id);

    /** All personal decks of a user: the notebook first, then by creation time. */
    @Query("SELECT d FROM VocabDeck d WHERE d.ownerId = :ownerId ORDER BY d.isNotebook DESC, d.createdAt ASC")
    java.util.List<VocabDeck> findPersonalDecks(@Param("ownerId") UUID ownerId);

    /** Personal decks that count towards the deck limit (the notebook does not). */
    @Query("SELECT COUNT(d) FROM VocabDeck d WHERE d.ownerId = :ownerId AND d.isNotebook = false")
    long countCustomDecks(@Param("ownerId") UUID ownerId);
}
