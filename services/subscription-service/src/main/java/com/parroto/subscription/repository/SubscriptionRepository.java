package com.parroto.subscription.repository;

import com.parroto.subscription.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findFirstByUserIdAndStatusAndEndAtAfterOrderByEndAtDesc(
            UUID userId, String status, OffsetDateTime now);

    List<Subscription> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Active subscriptions of several users (admin user list Premium column). */
    List<Subscription> findByUserIdInAndStatusAndEndAtAfter(Collection<UUID> userIds, String status, OffsetDateTime now);

    /** Rows of {@code [planId, distinct active users]}. */
    @Query("SELECT s.plan.id, COUNT(DISTINCT s.userId) FROM Subscription s "
            + "WHERE s.status = 'active' AND s.endAt > :now GROUP BY s.plan.id")
    List<Object[]> countActiveSubscribersByPlan(@Param("now") OffsetDateTime now);

    @Query("SELECT COUNT(DISTINCT s.userId) FROM Subscription s "
            + "WHERE s.plan.id = :planId AND s.status = 'active' AND s.endAt > :now")
    long countActiveSubscribersOfPlan(@Param("planId") String planId, @Param("now") OffsetDateTime now);

    @Query("SELECT COUNT(DISTINCT s.userId) FROM Subscription s WHERE s.status = 'active' AND s.endAt > :now")
    long countActiveSubscribers(@Param("now") OffsetDateTime now);
}
