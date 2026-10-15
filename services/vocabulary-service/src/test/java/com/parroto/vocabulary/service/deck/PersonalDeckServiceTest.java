package com.parroto.vocabulary.service.deck;

import com.parroto.common.cache.CacheService;
import com.parroto.common.exception.ApiException;
import com.parroto.vocabulary.dto.CardResponse;
import com.parroto.vocabulary.dto.LearnedWordResponse;
import com.parroto.vocabulary.dto.MyDeckDtos.PersonalCardRequest;
import com.parroto.vocabulary.dto.MyDeckDtos.PersonalDeckRequest;
import com.parroto.vocabulary.entity.VocabCard;
import com.parroto.vocabulary.entity.VocabDeck;
import com.parroto.vocabulary.repository.UserVocabRepository;
import com.parroto.vocabulary.repository.VocabCardRepository;
import com.parroto.vocabulary.repository.VocabDeckRepository;
import com.parroto.vocabulary.repository.VocabGroupRepository;
import com.parroto.vocabulary.service.PersonalWordService;
import com.parroto.vocabulary.service.PremiumClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PersonalDeckServiceTest {

    private final UUID userId = UUID.randomUUID();
    private VocabDeckRepository decks;
    private VocabCardRepository cards;
    private VocabGroupRepository groups;
    private PersonalWordService words;
    private PremiumClient premium;
    private PersonalDeckService service;

    @BeforeEach
    void setUp() {
        decks = mock(VocabDeckRepository.class);
        cards = mock(VocabCardRepository.class);
        groups = mock(VocabGroupRepository.class);
        words = mock(PersonalWordService.class);
        premium = mock(PremiumClient.class);
        DeckAccess access = new DeckAccess(decks, new com.parroto.common.security.AdminGuard("admin@x.com"));
        DeckCacheEvictor evictor = new DeckCacheEvictor(mock(CacheService.class));
        DeckGroupService groupService = new DeckGroupService(access, groups, cards, evictor);
        service = new PersonalDeckService(decks, cards, groups, mock(UserVocabRepository.class), words, premium,
                groupService, evictor);
        when(decks.saveAndFlush(any(VocabDeck.class))).thenAnswer(inv -> {
            VocabDeck d = inv.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            return d;
        });
        when(cards.save(any(VocabCard.class))).thenAnswer(inv -> {
            VocabCard c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });
        when(decks.findByOwnerIdForUpdate(userId)).thenReturn(Optional.of(notebook()));
    }

    private VocabDeck notebook() {
        return VocabDeck.builder().id(UUID.randomUUID()).name("Sổ tay cá nhân").ownerId(userId).isNotebook(true).build();
    }

    private VocabDeck custom(UUID owner) {
        VocabDeck d = VocabDeck.builder().id(UUID.randomUUID()).name("IELTS").ownerId(owner).isNotebook(false)
                .cardCount(0).build();
        when(decks.findByIdForUpdate(d.getId())).thenReturn(Optional.of(d));
        return d;
    }

    private static PersonalCardRequest card(String word, String meaning) {
        return new PersonalCardRequest(word, meaning, null, null, null, null, null);
    }

    private static void assertStatus(ApiException e, HttpStatus status) {
        assertThat(e).isNotNull();
        assertThat(e.getStatus()).isEqualTo(status);
    }

    // ---- decks ----

    @Test
    void freeUserCanCreateFiveDecksNotSix() {
        when(premium.isPremium(userId.toString())).thenReturn(false);
        when(decks.countCustomDecks(userId)).thenReturn(4L);
        var created = service.create(userId, new PersonalDeckRequest("  IELTS   Writing ", null));
        assertThat(created.name()).isEqualTo("IELTS Writing");
        assertThat(created.isNotebook()).isFalse();

        when(decks.countCustomDecks(userId)).thenReturn(5L);
        ApiException e = catchThrowableOfType(() -> service.create(userId, new PersonalDeckRequest("x", null)),
                ApiException.class);
        assertStatus(e, HttpStatus.BAD_REQUEST);
        assertThat(e.getMessage()).contains("tối đa 5 bộ thẻ");
    }

    @Test
    void premiumUserLimitIsFifty() {
        when(premium.isPremium(userId.toString())).thenReturn(true);
        when(decks.countCustomDecks(userId)).thenReturn(49L);
        service.create(userId, new PersonalDeckRequest("ok", null));
        when(decks.countCustomDecks(userId)).thenReturn(50L);
        assertStatus(catchThrowableOfType(() -> service.create(userId, new PersonalDeckRequest("no", null)),
                ApiException.class), HttpStatus.BAD_REQUEST);
    }

    @Test
    void deckNameIsRequired() {
        assertStatus(catchThrowableOfType(() -> service.create(userId, new PersonalDeckRequest(" ", null)),
                ApiException.class), HttpStatus.BAD_REQUEST);
    }

    @Test
    void otherUsersDeckIsNotFoundForEveryOperation() {
        VocabDeck theirs = custom(UUID.randomUUID());
        assertStatus(catchThrowableOfType(() -> service.update(userId, theirs.getId(), new PersonalDeckRequest("x", null)),
                ApiException.class), HttpStatus.NOT_FOUND);
        assertStatus(catchThrowableOfType(() -> service.delete(userId, theirs.getId()), ApiException.class),
                HttpStatus.NOT_FOUND);
        assertStatus(catchThrowableOfType(() -> service.addCard(userId, theirs.getId(), card("a", "b")),
                ApiException.class), HttpStatus.NOT_FOUND);
        verify(decks, never()).delete(any());
    }

    @Test
    void publicDeckIsNotFoundHere() {
        VocabDeck pub = VocabDeck.builder().id(UUID.randomUUID()).name("Public").build();
        when(decks.findByIdForUpdate(pub.getId())).thenReturn(Optional.of(pub));
        assertStatus(catchThrowableOfType(() -> service.addCard(userId, pub.getId(), card("a", "b")),
                ApiException.class), HttpStatus.NOT_FOUND);
    }

    @Test
    void notebookCannotBeRenamedOrDeleted() {
        VocabDeck nb = notebook();
        when(decks.findByIdForUpdate(nb.getId())).thenReturn(Optional.of(nb));
        assertStatus(catchThrowableOfType(() -> service.delete(userId, nb.getId()), ApiException.class),
                HttpStatus.BAD_REQUEST);
        assertStatus(catchThrowableOfType(() -> service.update(userId, nb.getId(), new PersonalDeckRequest("x", null)),
                ApiException.class), HttpStatus.BAD_REQUEST);
    }

    // ---- cards ----

    @Test
    void addCardToCustomDeckChecksDuplicatesAndTheCardLimit() {
        VocabDeck deck = custom(userId);
        when(premium.isPremium(anyString())).thenReturn(false);
        when(cards.normalizedWordsInDeck(deck.getId())).thenReturn(List.of("apple"));
        assertStatus(catchThrowableOfType(() -> service.addCard(userId, deck.getId(), card(" Apple ", "táo")),
                ApiException.class), HttpStatus.CONFLICT);

        when(cards.countCustomCards(userId)).thenReturn((long) VocabLimits.FREE_MAX_CARDS);
        ApiException e = catchThrowableOfType(() -> service.addCard(userId, deck.getId(), card("pear", "lê")),
                ApiException.class);
        assertStatus(e, HttpStatus.BAD_REQUEST);
        assertThat(e.getMessage()).contains("500").contains("còn trống 0");

        when(cards.countCustomCards(userId)).thenReturn(499L);
        when(cards.countByDeck_Id(deck.getId())).thenReturn(1L);
        CardResponse saved = service.addCard(userId, deck.getId(), card("pear", "lê"));
        assertThat(saved.word()).isEqualTo("pear");
        assertThat(saved.userVocabId()).isNull(); // custom decks: SRS starts when the card is first graded
        assertThat(deck.getCardCount()).isEqualTo(1);
    }

    @Test
    void addCardToNotebookUsesNotebookRules() {
        VocabDeck nb = notebook();
        when(decks.findByIdForUpdate(nb.getId())).thenReturn(Optional.of(nb));
        UUID cardId = UUID.randomUUID();
        UUID uvId = UUID.randomUUID();
        when(words.create(any(), any())).thenReturn(LearnedWordResponse.builder().userVocabId(uvId).cardId(cardId)
                .word("apple").meaning("táo").build());
        when(cards.findById(cardId)).thenReturn(Optional.of(VocabCard.builder().id(cardId).deck(nb).word("apple")
                .meaning("táo").build()));

        CardResponse r = service.addCard(userId, nb.getId(), card("apple", "táo"));

        assertThat(r.userVocabId()).isEqualTo(uvId);
        verify(words).create(any(), any());
        verify(cards, never()).countCustomCards(any()); // notebook keeps its own 2000 cap
    }

    @Test
    void groupOfAnotherDeckIsRejected() {
        VocabDeck deck = custom(userId);
        UUID foreignGroup = UUID.randomUUID();
        when(groups.findByIdAndDeckId(foreignGroup, deck.getId())).thenReturn(Optional.empty());
        PersonalCardRequest req = new PersonalCardRequest("a", "b", null, null, null, null, foreignGroup);
        assertStatus(catchThrowableOfType(() -> service.addCard(userId, deck.getId(), req), ApiException.class),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void cardsOfOtherUsersAreNotFound() {
        VocabDeck theirs = VocabDeck.builder().id(UUID.randomUUID()).ownerId(UUID.randomUUID()).build();
        UUID cardId = UUID.randomUUID();
        when(cards.findWithDeck(cardId)).thenReturn(Optional.of(VocabCard.builder().id(cardId).deck(theirs).build()));
        assertStatus(catchThrowableOfType(() -> service.updateCard(userId, cardId, card("a", "b")), ApiException.class),
                HttpStatus.NOT_FOUND);
        assertStatus(catchThrowableOfType(() -> service.deleteCard(userId, cardId), ApiException.class),
                HttpStatus.NOT_FOUND);
        verify(cards, never()).delete(any());
    }
}
