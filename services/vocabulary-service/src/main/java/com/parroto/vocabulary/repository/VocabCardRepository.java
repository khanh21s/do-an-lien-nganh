package com.parroto.vocabulary.repository;

import com.parroto.vocabulary.entity.VocabCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface VocabCardRepository extends JpaRepository<VocabCard, UUID> {

    @Query("""
            SELECT c FROM VocabCard c
            WHERE c.deck.id = :deckId
              AND (:searchWord IS NULL OR LOWER(c.word) LIKE LOWER(CONCAT('%', CAST(:searchWord AS string), '%')))
            ORDER BY c.word ASC
            """)
    Page<VocabCard> findByDeck(@Param("deckId") UUID deckId, @Param("searchWord") String searchWord, Pageable pageable);

    long countByDeck_Id(UUID deckId);

    boolean existsByDeck_IdAndWordIgnoreCase(UUID deckId, String word);
}
