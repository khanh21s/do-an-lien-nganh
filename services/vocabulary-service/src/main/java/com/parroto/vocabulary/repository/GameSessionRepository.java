package com.parroto.vocabulary.repository;

import com.parroto.vocabulary.entity.GameSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameSessionRepository extends JpaRepository<GameSession, UUID> {

    /** The user's own round, row-locked so answers/hints/finish of one round serialize. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM GameSession s WHERE s.id = :id AND s.userId = :userId")
    Optional<GameSession> findOwnForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    List<GameSession> findByUserIdAndStatus(UUID userId, String status);

    @Query("""
            SELECT MAX(s.score) FROM GameSession s
            WHERE s.userId = :userId AND s.mode = :mode AND s.sourceKey = :sourceKey
              AND s.status = 'FINISHED' AND s.id <> :excludeId
            """)
    Integer bestScore(@Param("userId") UUID userId, @Param("mode") String mode,
                      @Param("sourceKey") String sourceKey, @Param("excludeId") UUID excludeId);

    long countByUserIdAndStatus(UUID userId, String status);

    @Query("SELECT COALESCE(SUM(s.score), 0) FROM GameSession s WHERE s.userId = :userId AND s.status = 'FINISHED'")
    long totalScore(@Param("userId") UUID userId);

    /** The best finished round of each (mode, source) of the user; ties come back as several rows. */
    @Query("""
            SELECT s FROM GameSession s
            WHERE s.userId = :userId AND s.status = 'FINISHED'
              AND s.score = (SELECT MAX(s2.score) FROM GameSession s2
                             WHERE s2.userId = s.userId AND s2.status = 'FINISHED'
                               AND s2.mode = s.mode AND s2.sourceKey = s.sourceKey)
            ORDER BY s.finishedAt DESC
            """)
    List<GameSession> findBests(@Param("userId") UUID userId);

    // --- T16 ---

    Optional<GameSession> findByUserIdAndModeAndSourceKey(UUID userId, String mode, String sourceKey);

    long countByUserIdAndModeAndStatus(UUID userId, String mode, String status);

    /** Points of the user's rounds finished that started on {@code day} (daily goal). */
    @Query("""
            SELECT COALESCE(SUM(s.score), 0) FROM GameSession s
            WHERE s.userId = :userId AND s.status = 'FINISHED' AND s.playDate = :day
            """)
    long pointsOn(@Param("userId") UUID userId, @Param("day") LocalDate day);

    /**
     * Leaderboard of a mode over [from, to]: each user's best ranked round (0-point rounds left out), ordered by score then
     * by who got it first. Returns the top {@code limit} rows plus the row of {@code me}:
     * [userId, displayName, score, bestCombo, rank, totalPlayers].
     */
    @Query(value = """
            WITH best AS (
              SELECT DISTINCT ON (user_id) user_id, display_name, score, best_combo, finished_at
              FROM game_session
              WHERE status = 'FINISHED' AND ranked AND score > 0 AND mode = :mode AND play_date BETWEEN :from AND :to
              ORDER BY user_id, score DESC, finished_at ASC
            ), ranked AS (
              SELECT b.*, ROW_NUMBER() OVER (ORDER BY score DESC, finished_at ASC) AS rn,
                     COUNT(*) OVER () AS total
              FROM best b
            )
            SELECT CAST(user_id AS varchar), COALESCE(display_name, 'Người chơi'), score, best_combo, rn, total
            FROM ranked WHERE rn <= :limit OR user_id = :me ORDER BY rn
            """, nativeQuery = true)
    List<Object[]> leaderboard(@Param("mode") String mode, @Param("from") LocalDate from, @Param("to") LocalDate to,
                               @Param("limit") int limit, @Param("me") UUID me);
}
