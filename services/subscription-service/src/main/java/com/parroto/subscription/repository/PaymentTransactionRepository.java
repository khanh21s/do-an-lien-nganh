package com.parroto.subscription.repository;

import com.parroto.subscription.entity.PaymentTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID>,
        JpaSpecificationExecutor<PaymentTransaction> {

    /** Revenue rule (T14): successful transactions, gift codes excluded (they bring no money). */
    String PAID = "t.status = 'success' AND (t.method IS NULL OR t.method <> 'gift_code')";

    Optional<PaymentTransaction> findByTransactionId(String transactionId);

    Page<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Row lock so concurrent IPN / return-URL calls for the same order are processed exactly once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PaymentTransaction t WHERE t.id = :id")
    Optional<PaymentTransaction> findByIdForUpdate(@Param("id") UUID id);

    long countByStatus(String status);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM PaymentTransaction t WHERE " + PAID)
    long sumPaidRevenue();

    @Query("SELECT COUNT(t) FROM PaymentTransaction t WHERE " + PAID)
    long countPaid();

    @Query("SELECT t FROM PaymentTransaction t WHERE " + PAID + " AND t.createdAt >= :from")
    List<PaymentTransaction> findPaidSince(@Param("from") OffsetDateTime from);
}
