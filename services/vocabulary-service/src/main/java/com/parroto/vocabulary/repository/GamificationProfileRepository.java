package com.parroto.vocabulary.repository;

import com.parroto.vocabulary.entity.GamificationProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface GamificationProfileRepository extends JpaRepository<GamificationProfile, UUID> {

    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO gamification_profile (user_id) VALUES (:userId) ON CONFLICT (user_id) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM GamificationProfile p WHERE p.userId = :userId")
    Optional<GamificationProfile> findForUpdate(@Param("userId") UUID userId);

    /** Creates the row or refreshes the leaderboard name; writes nothing when the name is unchanged. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO gamification_profile (user_id, display_name) VALUES (:userId, :name)
            ON CONFLICT (user_id) DO UPDATE SET display_name = EXCLUDED.display_name, updated_at = CURRENT_TIMESTAMP
            WHERE gamification_profile.display_name IS DISTINCT FROM EXCLUDED.display_name
            """, nativeQuery = true)
    int upsertDisplayName(@Param("userId") UUID userId, @Param("name") String name);
}
