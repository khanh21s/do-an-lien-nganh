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

    /** NULL = public (admin) deck; otherwise one of the user's hidden personal decks (T13, P2-C). */
    @Column(name = "owner_id")
    private UUID ownerId;

    /**
     * P2-C: true for the user's notebook ("Sổ tay cá nhân": every card also has a user_vocab row,
     * due at once). A user can own several other personal decks (is_notebook = false).
     */
    @Column(name = "is_notebook", nullable = false)
    @Builder.Default
    private Boolean isNotebook = false;

    public boolean isNotebookDeck() {
        return Boolean.TRUE.equals(isNotebook);
    }

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
