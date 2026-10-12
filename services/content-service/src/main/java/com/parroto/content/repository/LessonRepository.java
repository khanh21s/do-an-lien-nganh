package com.parroto.content.repository;

import com.parroto.content.entity.Lesson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    @Query("""
            SELECT l FROM Lesson l
            WHERE l.isDeleted = false
              AND (:cefrLevel IS NULL OR l.cefrLevel = :cefrLevel)
              AND (:topicTag IS NULL OR l.topicTag = :topicTag)
            ORDER BY l.createdAt DESC
            """)
    Page<Lesson> search(@Param("cefrLevel") String cefrLevel, @Param("topicTag") String topicTag, Pageable pageable);

    @Query("""
            SELECT DISTINCT l FROM Lesson l LEFT JOIN Sentence s ON s.lesson = l
            WHERE l.isDeleted = false
              AND (LOWER(l.title) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(s.text) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(l.topicTag) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY l.createdAt DESC
            """)
    Page<Lesson> fullTextSearch(@Param("q") String query, Pageable pageable);

    boolean existsByIdAndIsDeletedFalse(UUID id);
}
