package com.parroto.content.entity;

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
@Table(name = "sentence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sentence {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "sentence_order", nullable = false)
    private Integer sentenceOrder;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    private String ipa;

    @Column(name = "translation_vi", columnDefinition = "text")
    private String translationVi;

    @Column(name = "start_ms")
    private Integer startMs;

    @Column(name = "end_ms")
    private Integer endMs;

    @Column(name = "audio_url")
    private String audioUrl;

    @Column(name = "image_url")
    private String imageUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
