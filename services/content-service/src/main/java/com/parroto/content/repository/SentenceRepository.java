package com.parroto.content.repository;

import com.parroto.content.entity.Sentence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SentenceRepository extends JpaRepository<Sentence, UUID> {

    @Query("SELECT s FROM Sentence s WHERE s.lesson.id = :lessonId ORDER BY s.sentenceOrder ASC")
    List<Sentence> findByLessonIdOrderBySentenceOrderAsc(@Param("lessonId") UUID lessonId);

    long countByLesson_Id(UUID lessonId);

    boolean existsByIdAndLesson_Id(UUID id, UUID lessonId);
}
