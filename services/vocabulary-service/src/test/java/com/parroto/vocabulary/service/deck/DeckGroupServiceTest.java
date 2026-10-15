package com.parroto.vocabulary.service.deck;

import com.parroto.common.cache.CacheService;
import com.parroto.common.exception.ApiException;
import com.parroto.common.security.AdminGuard;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.vocabulary.dto.MyDeckDtos.GroupOrderRequest;
import com.parroto.vocabulary.dto.MyDeckDtos.GroupRequest;
import com.parroto.vocabulary.dto.MyDeckDtos.MoveCardsRequest;
import com.parroto.vocabulary.entity.VocabDeck;
import com.parroto.vocabulary.entity.VocabGroup;
import com.parroto.vocabulary.repository.VocabCardRepository;
import com.parroto.vocabulary.repository.VocabDeckRepository;
import com.parroto.vocabulary.repository.VocabGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DeckGroupServiceTest {

    private final UUID ownerId = UUID.randomUUID();
    private VocabDeckRepository decks;
    private VocabGroupRepository groups;
    private VocabCardRepository cards;
    private DeckGroupService service;
    private VocabDeck personal;
    private VocabDeck publicDeck;

    private static AuthenticatedUser user(UUID id, String email) {
        return new AuthenticatedUser(id.toString(), email, "A1");
    }

    private final AuthenticatedUser owner = user(ownerId, "owner@x.com");
    private final AuthenticatedUser stranger = user(UUID.randomUUID(), "other@x.com");
    private final AuthenticatedUser admin = user(UUID.randomUUID(), "admin@x.com");

    @BeforeEach
    void setUp() {
        decks = mock(VocabDeckRepository.class);
        groups = mock(VocabGroupRepository.class);
        cards = mock(VocabCardRepository.class);
        service = new DeckGroupService(new DeckAccess(decks, new AdminGuard("admin@x.com")), groups, cards,
                new DeckCacheEvictor(mock(CacheService.class)));
        personal = VocabDeck.builder().id(UUID.randomUUID()).name("Mine").ownerId(ownerId).build();
        publicDeck = VocabDeck.builder().id(UUID.randomUUID()).name("Public").build();
        for (VocabDeck d : List.of(personal, publicDeck)) {
            when(decks.findById(d.getId())).thenReturn(Optional.of(d));
            when(decks.findByIdForUpdate(d.getId())).thenReturn(Optional.of(d));
        }
        when(groups.saveAndFlush(any(VocabGroup.class))).thenAnswer(inv -> {
            VocabGroup g = inv.getArgument(0);
            if (g.getId() == null) g.setId(UUID.randomUUID());
            return g;
        });
        when(groups.maxSortOrder(any())).thenReturn(-1);
    }

    private static HttpStatus statusOf(Runnable r) {
        ApiException e = catchThrowableOfType(r::run, ApiException.class);
        return e == null ? null : e.getStatus();
    }

    @Test
    void ownerManagesPersonalDeckOthersGet404() {
        assertThat(service.create(owner, personal.getId(), new GroupRequest(" Unit  1 ", null)).name())
                .isEqualTo("Unit 1");
        assertThat(statusOf(() -> service.create(stranger, personal.getId(), new GroupRequest("x", null))))
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(() -> service.create(admin, personal.getId(), new GroupRequest("x", null))))
                .isEqualTo(HttpStatus.NOT_FOUND); // admins do not see personal decks either
        assertThat(statusOf(() -> service.detail(null, personal.getId()))).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void publicDeckIsReadableByAllAndManagedByAdmins() {
        assertThat(service.detail(null, publicDeck.getId()).canManage()).isFalse();
        assertThat(service.detail(admin, publicDeck.getId()).canManage()).isTrue();
        assertThat(statusOf(() -> service.create(owner, publicDeck.getId(), new GroupRequest("x", null))))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(service.create(admin, publicDeck.getId(), new GroupRequest("Chapter 1", null)).sortOrder())
                .isZero();
    }

    @Test
    void duplicateAndEmptyNamesAreRejected() {
        when(groups.existsName(personal.getId(), "Unit 1")).thenReturn(true);
        assertThat(statusOf(() -> service.create(owner, personal.getId(), new GroupRequest("Unit 1", null))))
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(statusOf(() -> service.create(owner, personal.getId(), new GroupRequest("  ", null))))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOf(() -> service.create(owner, personal.getId(), new GroupRequest("x".repeat(101), null))))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void groupLimitPerDeck() {
        when(groups.countByDeckId(personal.getId())).thenReturn((long) VocabLimits.MAX_GROUPS_PER_DECK);
        assertThat(statusOf(() -> service.create(owner, personal.getId(), new GroupRequest("x", null))))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void reorderNeedsEveryGroupExactlyOnce() {
        List<VocabGroup> existing = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            existing.add(VocabGroup.builder().id(UUID.randomUUID()).deckId(personal.getId()).name("g" + i)
                    .sortOrder(i).build());
        }
        when(groups.findByDeckIdOrderBySortOrderAscCreatedAtAsc(personal.getId())).thenReturn(existing);
        List<UUID> reversed = List.of(existing.get(2).getId(), existing.get(1).getId(), existing.get(0).getId());
        service.reorder(owner, personal.getId(), new GroupOrderRequest(reversed));
        assertThat(existing.get(2).getSortOrder()).isZero();
        assertThat(existing.get(0).getSortOrder()).isEqualTo(2);

        assertThat(statusOf(() -> service.reorder(owner, personal.getId(),
                new GroupOrderRequest(List.of(existing.get(0).getId()))))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOf(() -> service.reorder(owner, personal.getId(), new GroupOrderRequest(List.of(
                existing.get(0).getId(), existing.get(0).getId(), existing.get(1).getId())))))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteKeepsCardsUngrouped() {
        VocabGroup g = VocabGroup.builder().id(UUID.randomUUID()).deckId(personal.getId()).name("g").build();
        when(groups.findByIdAndDeckId(g.getId(), personal.getId())).thenReturn(Optional.of(g));
        service.delete(owner, personal.getId(), g.getId());
        verify(groups).ungroupCards(g.getId());
        verify(groups).deleteById(g.getId());
        assertThat(statusOf(() -> service.delete(owner, personal.getId(), UUID.randomUUID())))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void moveCardsOnlyWithinTheDeckAndIntoItsGroups() {
        UUID g = UUID.randomUUID();
        when(groups.findByIdAndDeckId(g, personal.getId())).thenReturn(Optional.of(
                VocabGroup.builder().id(g).deckId(personal.getId()).name("g").build()));
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(groups.moveCards(eq(personal.getId()), any(), eq(g))).thenReturn(2);
        assertThat(service.moveCards(owner, personal.getId(), new MoveCardsRequest(ids, g)).moved()).isEqualTo(2);

        assertThat(statusOf(() -> service.moveCards(owner, personal.getId(),
                new MoveCardsRequest(ids, UUID.randomUUID())))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(() -> service.moveCards(owner, personal.getId(), new MoveCardsRequest(List.of(), null))))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        service.moveCards(owner, personal.getId(), new MoveCardsRequest(ids, null)); // ungroup
        verify(groups).moveCards(eq(personal.getId()), any(), eq(null));
    }

    @Test
    void detailCountsCardsPerGroup() {
        VocabGroup g = VocabGroup.builder().id(UUID.randomUUID()).deckId(personal.getId()).name("g").sortOrder(0).build();
        when(groups.findByDeckIdOrderBySortOrderAscCreatedAtAsc(personal.getId())).thenReturn(List.of(g));
        when(cards.countByGroup(personal.getId())).thenReturn(List.of(new Object[]{g.getId(), 3L},
                new Object[]{null, 2L}));
        var detail = service.detail(owner, personal.getId());
        assertThat(detail.personal()).isTrue();
        assertThat(detail.canManage()).isTrue();
        assertThat(detail.groups().get(0).cardCount()).isEqualTo(3);
        assertThat(detail.ungroupedCount()).isEqualTo(2);
    }
}
