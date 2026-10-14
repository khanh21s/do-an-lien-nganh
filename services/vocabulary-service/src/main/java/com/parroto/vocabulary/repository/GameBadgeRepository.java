package com.parroto.vocabulary.repository;

import com.parroto.vocabulary.entity.GameBadge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GameBadgeRepository extends JpaRepository<GameBadge, UUID> {

    List<GameBadge> findByUserId(UUID userId);
}
