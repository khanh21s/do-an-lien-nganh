package com.parroto.vocabulary.service;

import com.parroto.vocabulary.dto.CreatePersonalWordRequest;
import com.parroto.vocabulary.dto.LearnedWordResponse;
import com.parroto.vocabulary.entity.UserVocab;
import com.parroto.vocabulary.entity.VocabCard;
import com.parroto.vocabulary.entity.VocabDeck;
import com.parroto.vocabulary.exception.ApiException;
import com.parroto.vocabulary.repository.UserVocabRepository;
import com.parroto.vocabulary.repository.VocabCardRepository;
import com.parroto.vocabulary.repository.VocabDeckRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PersonalWordServiceTest {

    private final UUID userId = UUID.randomUUID();

    private UserVocabRepository userVocabRepository;
    private VocabCardRepository cardRepository;
    private VocabDeckRepository deckRepository;
    private CacheService cacheService;
    private PersonalWordService service;

    @BeforeEach
    void setUp() {
        userVocabRepository = mock(UserVocabRepository.class);
        cardRepository = mock(VocabCardRepository.class);
        deckRepository = mock(VocabDeckRepository.class);
        cacheService = mock(CacheService.class);
        service = new PersonalWordService(userVocabRepository, cardRepository, deckRepository, cacheService);
        when(cardRepository.save(any(VocabCard.class))).thenAnswer(inv -> {
            VocabCard c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });
        when(userVocabRepository.save(any(UserVocab.class))).thenAnswer(inv -> {
            UserVocab uv = inv.getArgument(0);
            if (uv.getId() == null) uv.setId(UUID.randomUUID());
            return uv;
        });
    }

    private VocabDeck personalDeck(UUID owner) {
        return VocabDeck.builder().id(UUID.randomUUID()).name(PersonalWordService.PERSONAL_DECK_NAME)
                .ownerId(owner).cardCount(0).build();
    }

    private static CreatePersonalWordRequest req(String word, String meaning) {
        return new CreatePersonalWordRequest(word, meaning, null, null, null, null);
    }

    private static void assertStatus(Throwable t, HttpStatus status, String message) {
        assertThat(t).isInstanceOf(ApiException.class);
        assertThat(((ApiException) t).getStatus()).isEqualTo(status);
        if (message != null) assertThat(t.getMessage()).isEqualTo(message);
    }

    private UserVocab userVocabIn(VocabDeck deck, UUID owner, String word) {
        VocabCard card = VocabCard.builder().id(UUID.randomUUID()).deck(deck).word(word).meaning("m").build();
        return UserVocab.builder().id(UUID.randomUUID()).userId(owner).card(card).build();
    }

    // ---- create ----

    @Test
    void createLazilyCreatesPersonalDeckOnFirstWord() {
        VocabDeck deck = personalDeck(userId);
        when(deckRepository.findByOwnerIdForUpdate(userId)).thenReturn(Optional.empty(), Optional.of(deck));

        LearnedWordResponse r = service.create(userId,
                new CreatePersonalWordRequest("  Serendipity ", " tình cờ may mắn ", " /ˌser.ənˈdɪp.ə.ti/ ", "",
                        "What a serendipity!", "   "));

        verify(deckRepository).insertPersonalDeckIfAbsent(userId, "Sổ tay cá nhân");
        assertThat(r.custom()).isTrue();
        assertThat(r.word()).isEqualTo("Serendipity");
        assertThat(r.meaning()).isEqualTo("tình cờ may mắn");
        assertThat(r.ipa()).isEqualTo("/ˌser.ənˈdɪp.ə.ti/");
        assertThat(r.partOfSpeech()).isNull();
        assertThat(r.example()).isEqualTo("What a serendipity!");
        assertThat(r.exampleVi()).isNull();
        assertThat(r.repetitions()).isZero();
        assertThat(r.nextReviewAt()).isBeforeOrEqualTo(OffsetDateTime.now()); // due now
        assertThat(deck.getCardCount()).isEqualTo(1);
        verify(userVocabRepository).existsWord(userId, "serendipity");
        verify(cacheService).invalidateByPattern("vocab:user:" + userId + ":*");
    }

    @Test
    void createReusesExistingPersonalDeck() {
        VocabDeck deck = personalDeck(userId);
        when(deckRepository.findByOwnerIdForUpdate(userId)).thenReturn(Optional.of(deck));
        when(cardRepository.countByDeck_Id(deck.getId())).thenReturn(5L);

        service.create(userId, req("apple", "quả táo"));

        verify(deckRepository, never()).insertPersonalDeckIfAbsent(any(), anyString());
        ArgumentCaptor<VocabCard> card = ArgumentCaptor.forClass(VocabCard.class);
        verify(cardRepository).save(card.capture());
        assertThat(card.getValue().getDeck()).isSameAs(deck);
        assertThat(deck.getCardCount()).isEqualTo(6);
    }

    @Test
    void createRejectsDuplicateIgnoringCaseAndWhitespace() {
        when(deckRepository.findByOwnerIdForUpdate(userId)).thenReturn(Optional.of(personalDeck(userId)));
        when(userVocabRepository.existsWord(userId, "apple")).thenReturn(true);

        Throwable t = catchThrowableOf(() -> service.create(userId, req("  APPLE  ", "táo")));

        assertStatus(t, HttpStatus.CONFLICT, "Từ này đã có trong sổ tay");
        verify(cardRepository, never()).save(any());
        verifyNoInteractions(cacheService);
    }

    @Test
    void createEnforces2000WordLimit() {
        VocabDeck deck = personalDeck(userId);
        when(deckRepository.findByOwnerIdForUpdate(userId)).thenReturn(Optional.of(deck));
        when(cardRepository.countByDeck_Id(deck.getId())).thenReturn(2000L);

        Throwable t = catchThrowableOf(() -> service.create(userId, req("apple", "táo")));

        assertStatus(t, HttpStatus.BAD_REQUEST, "Sổ tay cá nhân đã đạt giới hạn 2000 từ");
        verify(cardRepository, never()).save(any());
    }

    @Test
    void createAllowsWord1999() {
        VocabDeck deck = personalDeck(userId);
        when(deckRepository.findByOwnerIdForUpdate(userId)).thenReturn(Optional.of(deck));
        when(cardRepository.countByDeck_Id(deck.getId())).thenReturn(1999L);

        service.create(userId, req("apple", "táo"));
        assertThat(deck.getCardCount()).isEqualTo(2000);
    }

    @Test
    void createValidatesBeforeTouchingTheDatabase() {
        assertStatus(catchThrowableOf(() -> service.create(userId, req("   ", "táo"))), HttpStatus.BAD_REQUEST, null);
        assertStatus(catchThrowableOf(() -> service.create(userId, req("apple", null))), HttpStatus.BAD_REQUEST, null);
        assertStatus(catchThrowableOf(() -> service.create(userId, req("a".repeat(101), "táo"))),
                HttpStatus.BAD_REQUEST, "Từ tối đa 100 ký tự");
        assertStatus(catchThrowableOf(() -> service.create(userId, req("apple", "m".repeat(501)))),
                HttpStatus.BAD_REQUEST, "Nghĩa tối đa 500 ký tự");
        assertStatus(catchThrowableOf(() -> service.create(userId,
                        new CreatePersonalWordRequest("apple", "táo", "i".repeat(101), null, null, null))),
                HttpStatus.BAD_REQUEST, null);
        assertStatus(catchThrowableOf(() -> service.create(userId,
                        new CreatePersonalWordRequest("apple", "táo", null, "p".repeat(51), null, null))),
                HttpStatus.BAD_REQUEST, null);
        verifyNoInteractions(deckRepository, userVocabRepository, cardRepository);
    }

    @Test
    void normalizedAcceptsBoundaryLengthsAfterTrim() {
        CreatePersonalWordRequest n = new CreatePersonalWordRequest("  " + "a".repeat(100) + "  ", "m".repeat(500),
                "i".repeat(100), "p".repeat(50), "e".repeat(500), "v".repeat(500)).normalized();
        assertThat(n.word()).hasSize(100);
    }

    // ---- update ----

    @Test
    void updateChangesOwnCustomWord() {
        VocabDeck deck = personalDeck(userId);
        UserVocab uv = userVocabIn(deck, userId, "apple");
        when(userVocabRepository.findWithCardAndDeck(uv.getId())).thenReturn(Optional.of(uv));

        LearnedWordResponse r = service.update(userId, uv.getId(), req("Apple", "quả táo (mới)"));

        assertThat(r.meaning()).isEqualTo("quả táo (mới)");
        assertThat(r.word()).isEqualTo("Apple");
        assertThat(r.custom()).isTrue();
        verify(userVocabRepository).existsWordExcluding(userId, "apple", uv.getId());
        verify(cardRepository).save(uv.getCard());
    }

    @Test
    void updateConflictsOnlyWithOtherWords() {
        VocabDeck deck = personalDeck(userId);
        UserVocab uv = userVocabIn(deck, userId, "apple");
        when(userVocabRepository.findWithCardAndDeck(uv.getId())).thenReturn(Optional.of(uv));
        when(userVocabRepository.existsWordExcluding(userId, "banana", uv.getId())).thenReturn(true);

        assertStatus(catchThrowableOf(() -> service.update(userId, uv.getId(), req("Banana", "chuối"))),
                HttpStatus.CONFLICT, "Từ này đã có trong sổ tay");
        verify(cardRepository, never()).save(any());
    }

    @Test
    void updatePublicDeckWordIsForbidden() {
        VocabDeck publicDeck = VocabDeck.builder().id(UUID.randomUUID()).name("Public").build();
        UserVocab uv = userVocabIn(publicDeck, userId, "apple");
        when(userVocabRepository.findWithCardAndDeck(uv.getId())).thenReturn(Optional.of(uv));

        assertStatus(catchThrowableOf(() -> service.update(userId, uv.getId(), req("apple", "táo"))),
                HttpStatus.FORBIDDEN, "Chỉ sửa được từ bạn tự thêm");
        verify(cardRepository, never()).save(any());
    }

    @Test
    void updateOtherUsersWordIsNotFound() {
        UUID other = UUID.randomUUID();
        UserVocab uv = userVocabIn(personalDeck(other), other, "apple");
        when(userVocabRepository.findWithCardAndDeck(uv.getId())).thenReturn(Optional.of(uv));

        assertStatus(catchThrowableOf(() -> service.update(userId, uv.getId(), req("apple", "táo"))),
                HttpStatus.NOT_FOUND, "Không tìm thấy từ");
        assertStatus(catchThrowableOf(() -> service.update(userId, UUID.randomUUID(), req("apple", "táo"))),
                HttpStatus.NOT_FOUND, "Không tìm thấy từ");
    }

    // ---- remove ----

    @Test
    void removeCustomWordDeletesCardAndRecountsDeck() {
        VocabDeck deck = personalDeck(userId);
        deck.setCardCount(3);
        UserVocab uv = userVocabIn(deck, userId, "apple");
        when(userVocabRepository.findWithCardAndDeck(uv.getId())).thenReturn(Optional.of(uv));
        when(deckRepository.findByOwnerIdForUpdate(userId)).thenReturn(Optional.of(deck));
        when(cardRepository.countByDeck_Id(deck.getId())).thenReturn(2L);

        service.remove(userId, uv.getId());

        verify(userVocabRepository).delete(uv);
        verify(cardRepository).delete(uv.getCard());
        assertThat(deck.getCardCount()).isEqualTo(2);
        verify(deckRepository).save(deck);
        verify(cacheService).invalidateByPattern("vocab:deck:" + deck.getId() + ":*");
    }

    @Test
    void removePublicDeckWordKeepsCard() {
        VocabDeck publicDeck = VocabDeck.builder().id(UUID.randomUUID()).name("Public").build();
        UserVocab uv = userVocabIn(publicDeck, userId, "apple");
        when(userVocabRepository.findWithCardAndDeck(uv.getId())).thenReturn(Optional.of(uv));

        service.remove(userId, uv.getId());

        verify(userVocabRepository).delete(uv);
        verify(cardRepository, never()).delete(any());
        verify(deckRepository, never()).save(any());
    }

    @Test
    void removeOtherUsersWordIsNotFound() {
        UUID other = UUID.randomUUID();
        UserVocab uv = userVocabIn(personalDeck(other), other, "apple");
        when(userVocabRepository.findWithCardAndDeck(uv.getId())).thenReturn(Optional.of(uv));

        assertStatus(catchThrowableOf(() -> service.remove(userId, uv.getId())), HttpStatus.NOT_FOUND, "Không tìm thấy từ");
        verify(userVocabRepository, never()).delete(any());
        verify(cardRepository, never()).delete(any());
    }

    private static Throwable catchThrowableOf(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        return org.assertj.core.api.Assertions.catchThrowable(call);
    }
}
