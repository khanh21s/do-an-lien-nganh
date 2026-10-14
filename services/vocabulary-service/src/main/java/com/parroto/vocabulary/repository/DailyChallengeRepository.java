package com.parroto.vocabulary.repository;

import com.parroto.vocabulary.entity.DailyChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface DailyChallengeRepository extends JpaRepository<DailyChallenge, LocalDate> {

    /** The first player of the day fixes the cards; a concurrent first player loses silently. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO daily_challenge (day, card_ids) VALUES (:day, :cardIds) ON CONFLICT (day) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("day") LocalDate day, @Param("cardIds") String cardIds);
}
