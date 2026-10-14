package com.parroto.vocabulary.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One round of the "Dịch nhanh" game (T15/T16). Score and counters are maintained server-side. */
@Entity
@Table(name = "game_session")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameSession {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_FINISHED = "FINISHED";
    public static final String STATUS_ABANDONED = "ABANDONED";

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String mode;

    @Column(nullable = false)
    private String source;

    @Column(name = "source_key", nullable = false)
    private String sourceKey;

    @Column(name = "source_label", nullable = false)
    private String sourceLabel;

    @Column(nullable = false)
    @Builder.Default
    private String status = STATUS_ACTIVE;

    /** Comma-separated card ids not asked yet. */
    @Column(name = "card_queue", nullable = false, columnDefinition = "text")
    @Builder.Default
    private String cardQueue = "";

    /** Comma-separated {@code cardId:seq}: missed cards asked once more from that question number on. */
    @Column(name = "retry_queue", nullable = false, columnDefinition = "text")
    @Builder.Default
    private String retryQueue = "";

    @Builder.Default
    private Integer score = 0;

    @Builder.Default
    private Integer combo = 0;

    @Column(name = "best_combo")
    @Builder.Default
    private Integer bestCombo = 0;

    @Column(name = "correct_count")
    @Builder.Default
    private Integer correctCount = 0;

    @Column(name = "near_count")
    @Builder.Default
    private Integer nearCount = 0;

    @Column(name = "wrong_count")
    @Builder.Default
    private Integer wrongCount = 0;

    @Column(name = "skipped_count")
    @Builder.Default
    private Integer skippedCount = 0;

    @Column(name = "previous_best")
    private Integer previousBest;

    @Column(name = "srs_updated")
    @Builder.Default
    private Integer srsUpdated = 0;

    /** Lives left (SURVIVAL only). */
    private Integer lives;

    /** Correct answers without hints in under 3 s (badge "Tốc độ ánh sáng"). */
    @Column(name = "fast_count")
    @Builder.Default
    private Integer fastCount = 0;

    /** Masked email shown on leaderboards. */
    @Column(name = "display_name")
    private String displayName;

    /** Day the round started, Vietnam time (daily goal, leaderboards). */
    @Column(name = "play_date", nullable = false)
    private LocalDate playDate;

    /** Counts for leaderboards (public word sources only). */
    @Builder.Default
    private Boolean ranked = false;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public GameMode gameMode() {
        return GameMode.valueOf(mode);
    }
}
