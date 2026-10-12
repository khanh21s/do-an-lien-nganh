package com.parroto.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "\"user\"")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "auth_provider", nullable = false)
    @Builder.Default
    private String authProvider = "email";

    @Column(name = "cefr_level", nullable = false, length = 2)
    @Builder.Default
    private String cefrLevel = "B1";

    @Column(name = "total_hours_learned")
    @Builder.Default
    private BigDecimal totalHoursLearned = BigDecimal.ZERO;

    @Column(name = "total_words_learned")
    @Builder.Default
    private Integer totalWordsLearned = 0;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
