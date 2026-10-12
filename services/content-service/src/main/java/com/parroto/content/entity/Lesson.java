package com.parroto.content.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lesson")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lesson {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "cefr_level", length = 2)
    private String cefrLevel;

    @Column(name = "topic_tag")
    private String topicTag;

    @Column(name = "audio_url", nullable = false)
    private String audioUrl;

    private String description;

    private Integer duration;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
