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
 * P2-B: one XP gain of the app-wide XP ledger. {@code (source, refId)} is unique, so writing the
 * same award twice is a no-op (rows are inserted with ON CONFLICT DO NOTHING, see XpEventRepository).
 */
@Entity
@Table(name = "xp_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class XpEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "ref_id", nullable = false, length = 200)
    private String refId;

    @Column(nullable = false)
    private Integer amount;

    /** Day of the gain, Vietnam time. */
    @Column(nullable = false)
    private LocalDate day;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
