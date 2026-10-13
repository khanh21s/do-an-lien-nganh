package com.parroto.vocabulary.service;

import com.parroto.vocabulary.entity.VocabCard;
import com.parroto.vocabulary.entity.VocabDeck;
import com.parroto.vocabulary.exception.ApiException;
import com.parroto.vocabulary.repository.UserVocabRepository;
import com.parroto.vocabulary.repository.VocabCardRepository;
import com.parroto.vocabulary.repository.VocabDeckRepository;
import com.parroto.vocabulary.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** T13: personal decks are invisible to everyone but their owner, admin endpoints included. */
class DeckServicePersonalDeckTest {

    private final UUID ownerId = UUID.randomUUID();

    private VocabDeckRepository deckRepository;
    private VocabCardRepository cardRepository;
    private DeckService service;
    private VocabDeck personal;

    @BeforeEach
    void setUp() {
        deckRepository = mock(VocabDeckRepository.class);
        cardRepository = mock(VocabCardRepository.class);
        service = new DeckService(deckRepository, cardRepository, mock(UserVocabRepository.class),
                mock(PremiumClient.class), mock(CacheService.class));
        personal = VocabDeck.builder().id(UUID.randomUUID()).name("Sổ tay cá nhân").ownerId(ownerId).build();
        when(deckRepository.findById(personal.getId())).thenReturn(Optional.of(personal));
    }

    private static AuthenticatedUser user(UUID id) {
        return new AuthenticatedUser(id.toString(), "u@example.com", "A1");
    }

    private static void assertNotFound(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void personalDeckCardsAreNotFoundForGuestsAndOtherUsers() {
        assertNotFound(() -> service.getDeckCards(null, personal.getId(), null, 1, 20));
        assertNotFound(() -> service.getDeckCards(user(UUID.randomUUID()), personal.getId(), null, 1, 20));
        verifyNoInteractions(cardRepository);
    }

    @Test
    void ownerCanReadOwnPersonalDeckCards() {
        when(cardRepository.findByDeck(eq(personal.getId()), any(), any())).thenReturn(Page.empty());
        assertThat(service.getDeckCards(user(ownerId), personal.getId(), null, 1, 20).data()).isEmpty();
    }

    @Test
    void adminEndpointsTreatPersonalDecksAsMissing() {
        VocabCard card = VocabCard.builder().id(UUID.randomUUID()).deck(personal).word("w").meaning("m").build();
        when(cardRepository.findById(card.getId())).thenReturn(Optional.of(card));

        assertNotFound(() -> service.deleteDeck(personal.getId()));
        assertNotFound(() -> service.addCards(personal.getId(), null));
        assertNotFound(() -> service.deleteCard(card.getId()));
        assertNotFound(() -> service.updateCard(card.getId(), null));
        verify(deckRepository, never()).delete(any());
        verify(cardRepository, never()).delete(any());
    }
}
