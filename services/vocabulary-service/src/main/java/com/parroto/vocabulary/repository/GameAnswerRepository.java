package com.parroto.vocabulary.repository;

import com.parroto.vocabulary.entity.GameAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GameAnswerRepository extends JpaRepository<GameAnswer, UUID> {

    /** The question currently shown (at most one per round). */
    @Query("""
            SELECT a FROM GameAnswer a JOIN FETCH a.card c JOIN FETCH c.deck
            WHERE a.sessionId = :sessionId AND a.answeredAt IS NULL
            ORDER BY a.seq DESC
            """)
    List<GameAnswer> findPending(@Param("sessionId") UUID sessionId);

    @Query("""
            SELECT a FROM GameAnswer a JOIN FETCH a.card c JOIN FETCH c.deck
            WHERE a.sessionId = :sessionId AND a.answeredAt IS NOT NULL
            ORDER BY a.seq ASC
            """)
    List<GameAnswer> findAnswered(@Param("sessionId") UUID sessionId);

    long countBySessionIdAndAnsweredAtIsNotNull(UUID sessionId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM GameAnswer a WHERE a.sessionId = :sessionId AND a.answeredAt IS NULL")
    int deletePending(@Param("sessionId") UUID sessionId);

    /** T16 badge "Bậc thầy B2": correct answers of the user on cards of a deck level. */
    @Query("""
            SELECT COUNT(a) FROM GameAnswer a, GameSession s
            WHERE a.sessionId = s.id AND s.userId = :userId AND a.result = 'CORRECT' AND a.card.deck.level = :level
            """)
    long countCorrectOnLevel(@Param("userId") UUID userId, @Param("level") String level);
}
