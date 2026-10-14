package com.parroto.vocabulary.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A badge earned by a user (T16); codes are listed in {@code BadgeCatalog}. */
@Entity
@Table(name = "game_badge")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameBadge {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String code;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "earned_at", nullable = false)
    private OffsetDateTime earnedAt;
}
