package com.parroto.vocabulary.repository;

import com.parroto.vocabulary.entity.XpEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** P2-B XP ledger. Sums are computed on read (indexed by user/day), so there is no counter to drift. */
public interface XpEventRepository extends JpaRepository<XpEvent, Long> {

    /** @return 1 when the award was recorded, 0 when (source, refId) already exists. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO xp_event (user_id, source, ref_id, amount, day)
            VALUES (:userId, :source, :refId, :amount, :day)
            ON CONFLICT (source, ref_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") UUID userId, @Param("source") String source, @Param("refId") String refId,
                       @Param("amount") int amount, @Param("day") LocalDate day);

    @Query(value = "SELECT COALESCE(SUM(amount), 0) FROM xp_event WHERE user_id = :userId", nativeQuery = true)
    long totalXp(@Param("userId") UUID userId);

    @Query(value = """
            SELECT COALESCE(SUM(amount), 0) FROM xp_event
            WHERE user_id = :userId AND day BETWEEN :from AND :to
            """, nativeQuery = true)
    long xpBetween(@Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Per source on one day: [source, rowCount, xpSum]. */
    @Query(value = """
            SELECT source, COUNT(*), COALESCE(SUM(amount), 0) FROM xp_event
            WHERE user_id = :userId AND day = :day GROUP BY source
            """, nativeQuery = true)
    List<Object[]> statsOn(@Param("userId") UUID userId, @Param("day") LocalDate day);

    /**
     * XP leaderboard over [from, to]: users with XP > 0, ordered by XP then by who got there first.
     * Top {@code limit} rows plus the row of {@code me}: [userId, displayName, xp, rank, totalPlayers].
     */
    @Query(value = """
            WITH totals AS (
              SELECT user_id, SUM(amount) AS xp, MAX(created_at) AS last_gain
              FROM xp_event WHERE day BETWEEN :from AND :to
              GROUP BY user_id HAVING SUM(amount) > 0
            ), ranked AS (
              SELECT t.*, ROW_NUMBER() OVER (ORDER BY xp DESC, last_gain ASC, user_id) AS rn,
                     COUNT(*) OVER () AS total
              FROM totals t
            )
            SELECT CAST(r.user_id AS varchar), COALESCE(p.display_name, 'Người chơi'), r.xp, r.rn, r.total
            FROM ranked r LEFT JOIN gamification_profile p ON p.user_id = r.user_id
            WHERE r.rn <= :limit OR r.user_id = :me ORDER BY r.rn
            """, nativeQuery = true)
    List<Object[]> leaderboard(@Param("from") LocalDate from, @Param("to") LocalDate to,
                               @Param("limit") int limit, @Param("me") UUID me);
}
