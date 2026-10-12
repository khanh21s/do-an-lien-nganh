package com.parroto.practice.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "practice_attempt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeAttempt {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;

    @Column(name = "sentence_id")
    private UUID sentenceId;

    @Column(nullable = false, length = 20)
    private String mode; // dictation|shadowing

    private BigDecimal score;

    private BigDecimal accuracy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "word_scores", columnDefinition = "jsonb")
    private JsonNode wordScores;

    @Column(name = "audio_recording_url")
    private String audioRecordingUrl;

    private BigDecimal duration;

    @Column(length = 20)
    private String difficulty;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
