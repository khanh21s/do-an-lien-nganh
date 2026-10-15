package com.parroto.vocabulary.repository;

import com.parroto.vocabulary.entity.VocabGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** P2-C: groups inside a deck. */
public interface VocabGroupRepository extends JpaRepository<VocabGroup, UUID> {

    List<VocabGroup> findByDeckIdOrderBySortOrderAscCreatedAtAsc(UUID deckId);

    Optional<VocabGroup> findByIdAndDeckId(UUID id, UUID deckId);

    long countByDeckId(UUID deckId);

    @Query("SELECT COUNT(g) > 0 FROM VocabGroup g WHERE g.deckId = :deckId AND LOWER(g.name) = LOWER(:name) AND g.id <> :excludeId")
    boolean existsNameExcluding(@Param("deckId") UUID deckId, @Param("name") String name,
                                @Param("excludeId") UUID excludeId);

    @Query("SELECT COUNT(g) > 0 FROM VocabGroup g WHERE g.deckId = :deckId AND LOWER(g.name) = LOWER(:name)")
    boolean existsName(@Param("deckId") UUID deckId, @Param("name") String name);

    @Query("SELECT COALESCE(MAX(g.sortOrder), -1) FROM VocabGroup g WHERE g.deckId = :deckId")
    int maxSortOrder(@Param("deckId") UUID deckId);

    /** Cards keep existing when their group is deleted (FK ON DELETE SET NULL does it in SQL too). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE VocabCard c SET c.groupId = NULL WHERE c.groupId = :groupId")
    int ungroupCards(@Param("groupId") UUID groupId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE VocabCard c SET c.groupId = :groupId WHERE c.deck.id = :deckId AND c.id IN :cardIds")
    int moveCards(@Param("deckId") UUID deckId, @Param("cardIds") Collection<UUID> cardIds,
                  @Param("groupId") UUID groupId);
}
