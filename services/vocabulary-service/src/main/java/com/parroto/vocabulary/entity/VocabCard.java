package com.parroto.vocabulary.entity;

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
@Table(name = "vocab_card")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VocabCard {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deck_id", nullable = false)
    private VocabDeck deck;

    @Column(nullable = false)
    private String word;

    @Column(nullable = false, columnDefinition = "text")
    private String meaning;

    @Column(columnDefinition = "text")
    private String example;

    @Column(name = "example_vi", columnDefinition = "text")
    private String exampleVi;

    private String ipa;

    @Column(name = "audio_url")
    private String audioUrl;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "part_of_speech")
    private String partOfSpeech;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
