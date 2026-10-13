package com.parroto.vocabulary.service;

import com.parroto.vocabulary.dto.DeckResponse;
import com.parroto.vocabulary.dto.UpdateDeckRequest;
import com.parroto.vocabulary.entity.VocabDeck;
import com.parroto.vocabulary.exception.ApiException;
import com.parroto.vocabulary.repository.UserVocabRepository;
import com.parroto.vocabulary.repository.VocabCardRepository;
import com.parroto.vocabulary.repository.VocabDeckRepository;
import com.parroto.vocabulary.security.AdminGuard;
import com.parroto.vocabulary.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** T14-VOC: PUT /vocabs/decks/{id}. */
class DeckServiceUpdateDeckTest {

    private VocabDeckRepository deckRepository;
    private CacheService cacheService;
    private DeckService service;
    private VocabDeck deck;

    @BeforeEach
    void setUp() {
        deckRepository = mock(VocabDeckRepository.class);
        cacheService = mock(CacheService.class);
        service = new DeckService(deckRepository, mock(VocabCardRepository.class), mock(UserVocabRepository.class),
                mock(PremiumClient.class), cacheService);
        deck = VocabDeck.builder().id(UUID.randomUUID()).name("Old").description("old desc")
                .level("A1").isProOnly(false).cardCount(7).build();
        when(deckRepository.findById(deck.getId())).thenReturn(Optional.of(deck));
        when(deckRepository.saveAndFlush(any(VocabDeck.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static void assertStatus(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getMessage()).isEqualTo(message);
        });
    }

    @Test
    void updatesAllFieldsAndInvalidatesDeckListCache() {
        DeckResponse res = service.updateDeck(deck.getId(),
                new UpdateDeckRequest("  New name  ", "New desc", "b2", true));

        assertThat(res.id()).isEqualTo(deck.getId());
        assertThat(res.name()).isEqualTo("New name");
        assertThat(res.description()).isEqualTo("New desc");
        assertThat(res.level()).isEqualTo("B2");
        assertThat(res.isProOnly()).isTrue();
        assertThat(res.cardCount()).isEqualTo(7);
        verify(deckRepository).saveAndFlush(deck);
        verify(cacheService).invalidateByPattern("vocab:decks:*");
    }

    @Test
    void nullLevelAndBlankDescriptionAreCleared() {
        DeckResponse res = service.updateDeck(deck.getId(), new UpdateDeckRequest("N", "  ", null, null));
        assertThat(res.level()).isNull();
        assertThat(res.description()).isNull();
        assertThat(res.isProOnly()).isFalse();
    }

    @Test
    void validationErrorsAre400WithVietnameseMessages() {
        UUID id = deck.getId();
        assertStatus(() -> service.updateDeck(id, new UpdateDeckRequest(null, null, null, false)),
                HttpStatus.BAD_REQUEST, "Tên deck không được để trống");
        assertStatus(() -> service.updateDeck(id, new UpdateDeckRequest("   ", null, null, false)),
                HttpStatus.BAD_REQUEST, "Tên deck không được để trống");
        assertStatus(() -> service.updateDeck(id, new UpdateDeckRequest("x".repeat(256), null, null, false)),
                HttpStatus.BAD_REQUEST, "Tên deck tối đa 255 ký tự");
        assertStatus(() -> service.updateDeck(id, new UpdateDeckRequest("ok", null, "D1", false)),
                HttpStatus.BAD_REQUEST, "Trình độ phải là một trong A1, A2, B1, B2, C1, C2");
        assertStatus(() -> service.updateDeck(id, null), HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ");

        assertThat(service.updateDeck(id, new UpdateDeckRequest("x".repeat(255), null, null, false)).name())
                .hasSize(255);
        verify(deckRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void personalOrMissingDeckIs404() {
        VocabDeck personal = VocabDeck.builder().id(UUID.randomUUID()).name("P").ownerId(UUID.randomUUID()).build();
        when(deckRepository.findById(personal.getId())).thenReturn(Optional.of(personal));
        UUID missing = UUID.randomUUID();
        when(deckRepository.findById(missing)).thenReturn(Optional.empty());

        UpdateDeckRequest req = new UpdateDeckRequest("N", null, null, false);
        assertStatus(() -> service.updateDeck(personal.getId(), req), HttpStatus.NOT_FOUND, "Không tìm thấy deck");
        assertStatus(() -> service.updateDeck(missing, req), HttpStatus.NOT_FOUND, "Không tìm thấy deck");
        assertThat(personal.getName()).isEqualTo("P");
        verify(deckRepository, never()).saveAndFlush(any());
        verify(cacheService, never()).invalidateByPattern(anyString());
    }

    @Test
    void adminGuardRejectsNonAdminsWithVietnameseMessage() {
        AdminGuard guard = new AdminGuard("admin@parroto.vn");
        guard.requireAdmin(new AuthenticatedUser(UUID.randomUUID().toString(), "ADMIN@parroto.vn", "A1"));
        assertStatus(() -> guard.requireAdmin(new AuthenticatedUser(UUID.randomUUID().toString(), "u@x.vn", "A1")),
                HttpStatus.FORBIDDEN, "Bạn không có quyền quản trị");
        assertStatus(() -> guard.requireAdmin(null), HttpStatus.FORBIDDEN, "Bạn không có quyền quản trị");
    }
}
