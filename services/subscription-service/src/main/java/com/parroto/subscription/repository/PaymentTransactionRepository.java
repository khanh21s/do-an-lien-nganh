package com.parroto.subscription.repository;

import com.parroto.subscription.entity.PaymentTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByTransactionId(String transactionId);

    Page<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Row lock so concurrent IPN / return-URL calls for the same order are processed exactly once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PaymentTransaction t WHERE t.id = :id")
    Optional<PaymentTransaction> findByIdForUpdate(@Param("id") UUID id);
}
