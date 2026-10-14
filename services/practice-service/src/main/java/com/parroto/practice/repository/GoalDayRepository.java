package com.parroto.practice.repository;

import com.parroto.practice.entity.GoalDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

public interface GoalDayRepository extends JpaRepository<GoalDay, UUID> {

    /** Idempotent: recording the same (user, day, source) twice is a no-op. Returns rows inserted. */
    @Modifying
    @Query(value = """
            INSERT INTO goal_day (user_id, day, source) VALUES (:userId, :day, :source)
            ON CONFLICT (user_id, day, source) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") UUID userId, @Param("day") LocalDate day, @Param("source") String source);
}
