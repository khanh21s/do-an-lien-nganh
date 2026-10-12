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
@Table(name = "activation_code")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivationCode {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "used_by_user_id")
    private UUID usedByUserId;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
