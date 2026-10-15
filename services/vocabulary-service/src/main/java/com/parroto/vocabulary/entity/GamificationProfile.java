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

/**
 * P2-B: per-user gamification state that is not a ledger sum: daily check-in streak and the
 * masked display name shown on the XP leaderboard. Locked (SELECT … FOR UPDATE) while checking in.
 */
@Entity
@Table(name = "gamification_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GamificationProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "display_name", length = 64)
    private String displayName;

    @Column(name = "checkin_streak", nullable = false)
    @Builder.Default
    private Integer checkinStreak = 0;

    @Column(name = "last_checkin_day")
    private LocalDate lastCheckinDay;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
