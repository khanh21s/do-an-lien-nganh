package com.parroto.vocabulary.repository;

import com.parroto.vocabulary.entity.VocabDeck;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface VocabDeckRepository extends JpaRepository<VocabDeck, UUID> {

    @Query("""
            SELECT d FROM VocabDeck d
            WHERE (:level IS NULL OR d.level = :level)
              AND (:isProOnly IS NULL OR d.isProOnly = :isProOnly)
            ORDER BY d.createdAt DESC
            """)
    Page<VocabDeck> search(@Param("level") String level, @Param("isProOnly") Boolean isProOnly, Pageable pageable);
}
