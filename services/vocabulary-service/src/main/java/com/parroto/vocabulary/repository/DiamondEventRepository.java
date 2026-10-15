package com.parroto.vocabulary.repository;

import com.parroto.vocabulary.entity.DiamondEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** P2-B "kim cương" wallet ledger: balance = SUM(amount). */
public interface DiamondEventRepository extends JpaRepository<DiamondEvent, Long> {

    /** @return 1 when recorded, 0 when (source, refId) already exists. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO diamond_event (user_id, source, ref_id, amount, day)
            VALUES (:userId, :source, :refId, :amount, :day)
            ON CONFLICT (source, ref_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") UUID userId, @Param("source") String source, @Param("refId") String refId,
                       @Param("amount") int amount, @Param("day") LocalDate day);

    @Query(value = "SELECT COALESCE(SUM(amount), 0) FROM diamond_event WHERE user_id = :userId", nativeQuery = true)
    long balance(@Param("userId") UUID userId);

    /** ref ids of the rows of {@code source} on {@code day} (e.g. which missions were rewarded). */
    @Query(value = "SELECT ref_id FROM diamond_event WHERE user_id = :userId AND source = :source AND day = :day",
            nativeQuery = true)
    List<String> refsOn(@Param("userId") UUID userId, @Param("source") String source, @Param("day") LocalDate day);
}
