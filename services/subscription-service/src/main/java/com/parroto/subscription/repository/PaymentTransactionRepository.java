package com.parroto.subscription.repository;

import com.parroto.subscription.entity.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByTransactionId(String transactionId);

    Page<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
