package com.parroto.vocabulary.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One question of a game round; unanswered while {@code answeredAt} is null (T15). */
@Entity
@Table(name = "game_answer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameAnswer {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(nullable = false)
    private Integer seq;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private VocabCard card;

    @Column(name = "is_retry", nullable = false)
    @Builder.Default
    private Boolean isRetry = false;

    @Column(name = "asked_at", nullable = false)
    private OffsetDateTime askedAt;

    @Column(name = "answered_at")
    private OffsetDateTime answeredAt;

    private String answer;

    /** {@link GameResult} name, null while unanswered. */
    private String result;

    @Column(name = "hints_used", nullable = false)
    @Builder.Default
    private Integer hintsUsed = 0;

    @Builder.Default
    private Integer points = 0;
}
