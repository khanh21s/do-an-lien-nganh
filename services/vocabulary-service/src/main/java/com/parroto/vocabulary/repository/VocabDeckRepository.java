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
     * The user's personal deck, row-locked for the rest of the transaction so concurrent
     * adds/edits by the same user (duplicate check, 2000-word limit, card_count) serialize.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM VocabDeck d WHERE d.ownerId = :ownerId")
    Optional<VocabDeck> findByOwnerIdForUpdate(@Param("ownerId") UUID ownerId);

    /** Lazily creates the personal deck; a concurrent creator wins silently (unique ux_vocab_deck_owner). */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO vocab_deck (name, is_pro_only, card_count, owner_id)
            VALUES (:name, false, 0, :ownerId)
            ON CONFLICT (owner_id) WHERE owner_id IS NOT NULL DO NOTHING
            """, nativeQuery = true)
    int insertPersonalDeckIfAbsent(@Param("ownerId") UUID ownerId, @Param("name") String name);
}
