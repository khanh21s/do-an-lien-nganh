package com.parroto.vocabulary.dto;

import com.parroto.vocabulary.entity.VocabDeck;
import com.parroto.vocabulary.entity.VocabGroup;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * P2-C: personal decks ("Bộ thẻ của tôi"), groups and deck detail. Request bodies are validated
 * in the services (400 with Vietnamese messages) rather than with Bean Validation (422 here).
 */
public final class MyDeckDtos {

    private MyDeckDtos() {
    }

    /** Body of POST /vocabs/user/decks and PUT /vocabs/user/decks/{id}. */
    public record PersonalDeckRequest(String name, String description) {
    }

    /** One personal deck in GET /vocabs/user/decks. */
    public record PersonalDeckResponse(
            UUID id,
            String name,
            String description,
            boolean isNotebook,
            int cardCount,
            int groupCount,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static PersonalDeckResponse from(VocabDeck d, int groupCount) {
            return new PersonalDeckResponse(d.getId(), d.getName(), d.getDescription(), d.isNotebookDeck(),
                    d.getCardCount() == null ? 0 : d.getCardCount(), groupCount, d.getCreatedAt(), d.getUpdatedAt());
        }
    }

    /** Usage of the free/premium limits (the notebook has its own cap). */
    public record DeckUsage(
            boolean premium,
            long decks,
            int maxDecks,
            long cards,
            int maxCards,
            long notebookCards,
            int maxNotebookCards
    ) {
    }

    /** GET /vocabs/user/decks. */
    public record PersonalDeckList(List<PersonalDeckResponse> decks, DeckUsage usage) {
    }

    /** Body of POST /vocabs/user/decks/{id}/cards and PUT /vocabs/user/cards/{cardId}. */
    public record PersonalCardRequest(
            String word,
            String meaning,
            String ipa,
            String partOfSpeech,
            String example,
            String exampleVi,
            /** Null = ungrouped (on PUT the card is moved accordingly). */
            UUID groupId
    ) {
        public CreatePersonalWordRequest asWordRequest() {
            return new CreatePersonalWordRequest(word, meaning, ipa, partOfSpeech, example, exampleVi);
        }
    }

    /** Body of POST /vocabs/decks/{deckId}/groups and PUT .../groups/{groupId}. */
    public record GroupRequest(String name, Integer sortOrder) {
    }

    /** Body of PUT /vocabs/decks/{deckId}/groups/order: every group id of the deck, in the new order. */
    public record GroupOrderRequest(List<UUID> groupIds) {
    }

    /** Body of PUT /vocabs/decks/{deckId}/cards/group. {@code groupId} null = ungroup. */
    public record MoveCardsRequest(List<UUID> cardIds, UUID groupId) {
    }

    public record MoveCardsResponse(int moved) {
    }

    public record GroupResponse(UUID id, UUID deckId, String name, int sortOrder, long cardCount) {
        public static GroupResponse from(VocabGroup g, long cardCount) {
            return new GroupResponse(g.getId(), g.getDeckId(), g.getName(),
                    g.getSortOrder() == null ? 0 : g.getSortOrder(), cardCount);
        }
    }

    /** GET /vocabs/decks/{deckId}: a public deck, or a personal deck of the caller. */
    public record DeckDetailResponse(
            UUID id,
            String name,
            String description,
            String level,
            boolean isProOnly,
            int cardCount,
            boolean personal,
            boolean isNotebook,
            /** The caller may edit groups/cards/import here (owner of a personal deck, admin of a public one). */
            boolean canManage,
            List<GroupResponse> groups,
            long ungroupedCount,
            OffsetDateTime createdAt
    ) {
    }
}
