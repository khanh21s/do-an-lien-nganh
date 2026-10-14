package com.parroto.vocabulary.repository;

import com.parroto.vocabulary.entity.GameProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface GameProfileRepository extends JpaRepository<GameProfile, UUID> {

    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO game_profile (user_id) VALUES (:userId) ON CONFLICT (user_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM GameProfile p WHERE p.userId = :userId")
    Optional<GameProfile> findForUpdate(@Param("userId") UUID userId);
}
