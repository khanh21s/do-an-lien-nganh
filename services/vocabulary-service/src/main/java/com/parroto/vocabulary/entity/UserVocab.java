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

    /** V4: Anki state; a new row is a NEW card (never graded). */
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    @Builder.Default
    private CardState state = CardState.NEW;

    /** V4: index into the learning/relearning steps (0 for NEW/REVIEW). */
    @Column(name = "step", nullable = false)
    @Builder.Default
    private Integer step = 0;

    /** V4: how often the card lapsed (Again on a REVIEW card). */
    @Column(name = "lapses", nullable = false)
    @Builder.Default
    private Integer lapses = 0;

    /** REVIEW: current interval; RELEARNING: interval after relearning; NEW/LEARNING: 0. */
    @Column(name = "interval_days")
    @Builder.Default
    private Integer intervalDays = 0;

    @Column(name = "ease_factor")
    @Builder.Default
    private BigDecimal easeFactor = new BigDecimal("2.50");

    /** Number of answers given to this card (Anki "reviews"); before V4 it was SM-2's success streak. */
    @Builder.Default
    private Integer repetitions = 0;

    /** Last answer as SM-2 quality: Again=1, Hard=3, Good=4, Easy=5 ({@code Rating.legacyQuality}). */
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
