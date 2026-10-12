package com.parroto.vocabulary.entity;

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
@Table(name = "user_vocab")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserVocab {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private VocabCard card;

    @Column(name = "next_review_at")
    @Builder.Default
    private OffsetDateTime nextReviewAt = OffsetDateTime.now();

    @Column(name = "interval_days")
    @Builder.Default
    private Integer intervalDays = 1;

    @Column(name = "ease_factor")
    @Builder.Default
    private BigDecimal easeFactor = BigDecimal.valueOf(2.5);

    @Builder.Default
    private Integer repetitions = 0;

    private Integer quality;

    @Column(name = "last_review_at")
    private OffsetDateTime lastReviewAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
