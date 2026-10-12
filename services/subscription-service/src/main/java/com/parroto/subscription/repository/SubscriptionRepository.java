package com.parroto.subscription.repository;

import com.parroto.subscription.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findFirstByUserIdAndStatusAndEndAtAfterOrderByEndAtDesc(
            UUID userId, String status, OffsetDateTime now);

    List<Subscription> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
