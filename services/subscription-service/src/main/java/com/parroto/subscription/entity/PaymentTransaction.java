package com.parroto.subscription.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "plan_id")
    private String planId;

    @Column(nullable = false)
    private Long amount;

    private String method; // vnpay|momo|gift_code

    @Column(length = 20)
    @Builder.Default
    private String status = "pending"; // pending|success|failed

    @Column(name = "transaction_id", unique = true)
    private String transactionId;

    @Column(columnDefinition = "text")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
