package com.parroto.vocabulary.service.importer;

import com.parroto.common.cache.CacheService;
import com.parroto.common.exception.ApiException;
import com.parroto.common.security.AdminGuard;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.vocabulary.dto.ImportDtos.ImportCommitRequest;
import com.parroto.vocabulary.dto.ImportDtos.ImportCommitResponse;
import com.parroto.vocabulary.dto.ImportDtos.ImportPreviewResponse;
import com.parroto.vocabulary.dto.ImportDtos.ImportRowInput;
import com.parroto.vocabulary.entity.UserVocab;
import com.parroto.vocabulary.entity.VocabCard;
import com.parroto.vocabulary.entity.VocabDeck;
import com.parroto.vocabulary.entity.VocabGroup;
import com.parroto.vocabulary.repository.UserVocabRepository;
import com.parroto.vocabulary.repository.VocabCardRepository;
import com.parroto.vocabulary.repository.VocabDeckRepository;
import com.parroto.vocabulary.repository.VocabGroupRepository;
import com.parroto.vocabulary.service.PremiumClient;
import com.parroto.vocabulary.service.deck.DeckAccess;
import com.parroto.vocabulary.service.deck.DeckCacheEvictor;
import com.parroto.vocabulary.service.deck.DeckGroupService;
import com.parroto.vocabulary.service.deck.VocabLimits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class VocabImportServiceTest {

    private final UUID ownerId = UUID.randomUUID();
    private final AuthenticatedUser owner = new AuthenticatedUser(ownerId.toString(), "me@x.com", "A1");
    private final AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID().toString(), "admin@x.com", "A1");

    private VocabDeckRepository decks;
    private VocabCardRepository cards;
    private VocabGroupRepository groups;
    private UserVocabRepository userVocabs;
    private PremiumClient premium;
    private VocabImportService service;
    private VocabDeck custom;
    private VocabDeck notebook;
    private VocabDeck publicDeck;

    @BeforeEach
    void setUp() {
        decks = mock(VocabDeckRepository.class);
        cards = mock(VocabCardRepository.class);
        groups = mock(VocabGroupRepository.class);
        userVocabs = mock(UserVocabRepository.class);
        premium = mock(PremiumClient.class);
        DeckAccess access = new DeckAccess(decks, new AdminGuard("admin@x.com"));
        DeckCacheEvictor evictor = new DeckCacheEvictor(mock(CacheService.class));
        service = new VocabImportService(access, new DeckGroupService(access, groups, cards, evictor), decks, cards,
                userVocabs, premium, evictor);
        custom = deck(ownerId, false);
        notebook = deck(ownerId, true);
        publicDeck = deck(null, false);
        when(cards.saveAll(anyList())).thenAnswer(inv -> {
            List<VocabCard> list = inv.getArgument(0);
            list.forEach(c -> c.setId(UUID.randomUUID()));
            return list;
        });
        when(groups.saveAndFlush(any(VocabGroup.class))).thenAnswer(inv -> {
            VocabGroup g = inv.getArgument(0);
            g.setId(UUID.randomUUID());
            return g;
        });
    }

    private VocabDeck deck(UUID owner, boolean isNotebook) {
        VocabDeck d = VocabDeck.builder().id(UUID.randomUUID()).name("d").ownerId(owner).isNotebook(isNotebook)
                .cardCount(0).build();
        when(decks.findById(d.getId())).thenReturn(Optional.of(d));
        when(decks.findByIdForUpdate(d.getId())).thenReturn(Optional.of(d));
        return d;
    }

    private static ImportRowInput row(String word, String meaning) {
        return new ImportRowInput(word, meaning, null, null, null, null);
    }

    @Test
    void commitSkipsDuplicatesAndInvalidRowsAndReportsCounts() {
        when(cards.normalizedWordsInDeck(custom.getId())).thenReturn(List.of("book"));
        List<ImportRowInput> rows = List.of(row("apple", "táo"), row("Apple", "táo 2"), row("book", "sách"),
                row("pen", ""), row(null, null), row("cat", "mèo"));

        ImportCommitResponse r = service.commit(owner, custom.getId(), new ImportCommitRequest(null, "Unit 1", rows));

        assertThat(r.received()).isEqualTo(6);
        assertThat(r.imported()).isEqualTo(2);
        assertThat(r.skippedDuplicate()).isEqualTo(2);
        assertThat(r.skippedInvalid()).isEqualTo(2);
        assertThat(r.errors()).extracting(e -> e.index()).containsExactly(3, 4);
        assertThat(r.groupId()).isNotNull(); // "Unit 1" was created
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<VocabCard>> saved = ArgumentCaptor.forClass(List.class);
        verify(cards).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(VocabCard::getWord).containsExactly("apple", "cat");
        assertThat(saved.getValue()).allMatch(c -> r.groupId().equals(c.getGroupId()));
        verify(userVocabs, never()).saveAll(anyList()); // custom deck: no SRS rows
    }

    @Test
    void commitEnforcesTheFreeCardLimitBeforeSaving() {
        when(premium.isPremium(anyString())).thenReturn(false);
        when(cards.countCustomCards(ownerId)).thenReturn(499L);
        ApiException e = catchThrowableOfType(() -> service.commit(owner, custom.getId(),
                new ImportCommitRequest(null, null, List.of(row("a", "1"), row("b", "2")))), ApiException.class);
        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getMessage()).contains("còn trống 1");
        verify(cards, never()).saveAll(anyList());
    }

    @Test
    void premiumCardLimitIs8000() {
        when(premium.isPremium(anyString())).thenReturn(true);
        when(cards.countCustomCards(ownerId)).thenReturn(7_999L);
        assertThat(service.commit(owner, custom.getId(), new ImportCommitRequest(null, null, List.of(row("a", "1"))))
                .imported()).isEqualTo(1);
    }

    @Test
    void notebookImportSchedulesReviewsAndUsesTheNotebookCap() {
        when(cards.normalizedNotebookWords(ownerId)).thenReturn(List.of("hello")); // learned from a public deck
        ImportCommitResponse r = service.commit(owner, notebook.getId(), new ImportCommitRequest(null, null,
                List.of(row("hello", "xin chào"), row("bye", "tạm biệt"))));
        assertThat(r.imported()).isEqualTo(1);
        assertThat(r.skippedDuplicate()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserVocab>> uv = ArgumentCaptor.forClass(List.class);
        verify(userVocabs).saveAll(uv.capture());
        assertThat(uv.getValue()).hasSize(1).allMatch(u -> u.getUserId().equals(ownerId) && u.getRepetitions() == 0);

        when(cards.countByDeck_Id(notebook.getId())).thenReturn((long) VocabLimits.NOTEBOOK_MAX_CARDS);
        assertThat(catchThrowableOfType(() -> service.commit(owner, notebook.getId(),
                new ImportCommitRequest(null, null, List.of(row("new", "mới")))), ApiException.class).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void ownershipRules() {
        AuthenticatedUser stranger = new AuthenticatedUser(UUID.randomUUID().toString(), "s@x.com", "A1");
        var req = new ImportCommitRequest(null, null, List.of(row("a", "b")));
        assertThat(catchThrowableOfType(() -> service.commit(stranger, custom.getId(), req), ApiException.class)
                .getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(catchThrowableOfType(() -> service.commit(owner, publicDeck.getId(), req), ApiException.class)
                .getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        // admins import into public decks without the personal limits
        assertThat(service.commit(admin, publicDeck.getId(), req).imported()).isEqualTo(1);
        verify(premium, never()).isPremium(anyString());
    }

    @Test
    void rowLimitAndEmptyBody() {
        List<ImportRowInput> many = new ArrayList<>(Collections.nCopies(VocabLimits.IMPORT_MAX_ROWS + 1, row("a", "b")));
        assertThat(catchThrowableOfType(() -> service.commit(admin, publicDeck.getId(),
                new ImportCommitRequest(null, null, many)), ApiException.class).getMessage()).contains("tối đa 2.000");
        assertThat(catchThrowableOfType(() -> service.commit(admin, publicDeck.getId(),
                new ImportCommitRequest(null, null, List.of())), ApiException.class).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void previewParsesWithoutSavingAndFlagsWordsAlreadyInTheDeck() {
        when(cards.normalizedWordsInDeck(custom.getId())).thenReturn(List.of("book"));
        MockMultipartFile file = new MockMultipartFile("file", "words.txt", "text/plain",
                "apple - táo\nbook - sách\nbroken line\napple: táo nữa\n".getBytes(StandardCharsets.UTF_8));

        ImportPreviewResponse p = service.preview(owner, file, custom.getId());

        assertThat(p.format()).isEqualTo("txt");
        assertThat(p.totalRows()).isEqualTo(4);
        assertThat(p.validRows()).isEqualTo(1);
        assertThat(p.invalidRows()).isEqualTo(1);
        assertThat(p.duplicateRows()).isEqualTo(2);
        verify(cards, never()).saveAll(anyList());
    }

    @Test
    void previewLimits() {
        MockMultipartFile big = new MockMultipartFile("file", "big.csv", "text/csv",
                new byte[(int) VocabLimits.IMPORT_MAX_BYTES + 1]);
        assertThat(catchThrowableOfType(() -> service.preview(owner, big, null), ApiException.class).getMessage())
                .isEqualTo("File vượt quá 2 MB");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= VocabLimits.IMPORT_MAX_ROWS; i++) sb.append("w").append(i).append(",nghĩa\n");
        MockMultipartFile rows = new MockMultipartFile("file", "rows.csv", "text/csv",
                sb.toString().getBytes(StandardCharsets.UTF_8));
        assertThat(catchThrowableOfType(() -> service.preview(owner, rows, null), ApiException.class).getMessage())
                .contains("2.001 dòng");
        MockMultipartFile empty = new MockMultipartFile("file", "e.txt", "text/plain", new byte[0]);
        assertThat(catchThrowableOfType(() -> service.preview(owner, empty, null), ApiException.class).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
