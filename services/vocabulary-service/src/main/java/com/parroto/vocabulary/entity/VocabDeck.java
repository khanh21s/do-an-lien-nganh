package com.parroto.vocabulary.entity;

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
@Table(name = "vocab_deck")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VocabDeck {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(length = 2)
    private String level;

    @Column(name = "is_pro_only")
    @Builder.Default
    private Boolean isProOnly = false;

    @Column(name = "card_count")
    @Builder.Default
    private Integer cardCount = 0;

    @Column(name = "created_by")
    private UUID createdBy;

    /** NULL = public (admin) deck; otherwise the user's hidden personal deck (T13). */
    @Column(name = "owner_id")
    private UUID ownerId;

    public boolean isPersonal() {
        return ownerId != null;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId != null && ownerId.equals(userId);
    }

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
