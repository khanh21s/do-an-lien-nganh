package com.parroto.vocabulary.service;

import com.parroto.common.exception.ApiException;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.vocabulary.dto.StudyDtos.RatingPreview;
import com.parroto.vocabulary.dto.StudyDtos.StudySession;
import com.parroto.vocabulary.dto.SubmitReviewRequest;
import com.parroto.vocabulary.dto.SubmitReviewResponse;
import com.parroto.vocabulary.entity.CardState;
import com.parroto.vocabulary.entity.UserVocab;
import com.parroto.vocabulary.entity.VocabCard;
import com.parroto.vocabulary.entity.VocabDeck;
import com.parroto.vocabulary.repository.UserVocabRepository;
import com.parroto.vocabulary.repository.VocabCardRepository;
import com.parroto.vocabulary.repository.VocabDeckRepository;
import com.parroto.vocabulary.service.srs.AnkiScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Session ordering, Pro/ownership rules and grading of the P2-anki study service (repositories mocked). */
class StudyServiceTest {

    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-15T10:00:00+07:00");
    static final OffsetDateTime CUTOFF = OffsetDateTime.parse("2026-10-16T04:00:00+07:00");
    static final UUID NO_GROUP = new UUID(0, 0);

    UserVocabRepository uvRepo;
    VocabCardRepository cardRepo;
    VocabDeckRepository deckRepo;
    PremiumClient premium;
    StudyService service;

    final UUID userId = UUID.randomUUID();
    final AuthenticatedUser principal = new AuthenticatedUser(userId.toString(), "u@test.local", "B1");
    VocabDeck deck;

    @BeforeEach
    void setUp() {
        uvRepo = mock(UserVocabRepository.class);
        cardRepo = mock(VocabCardRepository.class);
        deckRepo = mock(VocabDeckRepository.class);
        premium = mock(PremiumClient.class);
        Clock clock = Clock.fixed(NOW.toInstant(), ZoneOffset.ofHours(7));
        service = new StudyService(uvRepo, cardRepo, deckRepo, premium, new AnkiScheduler(), clock);
        deck = VocabDeck.builder().id(UUID.randomUUID()).name("B2").isProOnly(false).build();
        when(deckRepo.findById(deck.getId())).thenReturn(Optional.of(deck));
        when(uvRepo.deckStudyCounts(any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{2L, 3L, 700L}));
        when(uvRepo.saveAndFlush(any(UserVocab.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    VocabCard card(String word) {
        return VocabCard.builder().id(UUID.randomUUID()).deck(deck).word(word).meaning("nghĩa " + word).build();
    }

    UserVocab row(VocabCard card, CardState state, int interval, OffsetDateTime due) {
        return UserVocab.builder().id(UUID.randomUUID()).userId(userId).card(card).state(state)
                .intervalDays(interval).easeFactor(new BigDecimal("2.50")).nextReviewAt(due).build();
    }

    StudySession deckSession() {
        return service.deckSession(principal, deck.getId(), null, "seed1");
    }

    private void learningDue(OffsetDateTime until, UserVocab... rows) {
        when(uvRepo.deckLearningDue(eq(userId), eq(deck.getId()), eq(0), eq(NO_GROUP), eq(until), any()))
                .thenReturn(List.of(rows));
    }

    @Test
    void countsAreNewLearningReview() {
        StudySession s = deckSession();
        assertThat(s.counts().newCount()).isEqualTo(700);
        assertThat(s.counts().learning()).isEqualTo(2);
        assertThat(s.counts().review()).isEqualTo(3);
        verify(uvRepo).deckStudyCounts(userId, deck.getId(), 0, NO_GROUP, NOW.plusMinutes(20), CUTOFF);
    }

    @Test
    void dueLearningCardsComeFirst() {
        UserVocab learning = row(card("learn"), CardState.LEARNING, 0, NOW.minusMinutes(1));
        learningDue(NOW, learning);
        when(uvRepo.deckReviewDue(any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(List.of(row(card("rev"), CardState.REVIEW, 3, NOW.minusDays(1))));

        StudySession s = deckSession();
        assertThat(s.card().word()).isEqualTo("learn");
        assertThat(s.card().queue()).isEqualTo("learning");
        verify(uvRepo, never()).deckReviewDue(any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void thenReviewCardsDueToday() {
        learningDue(NOW);
        UserVocab review = row(card("rev"), CardState.REVIEW, 10, OffsetDateTime.parse("2026-10-15T04:00:00+07:00"));
        when(uvRepo.deckReviewDue(userId, deck.getId(), 0, NO_GROUP, CUTOFF, "seed1")).thenReturn(List.of(review));

        StudySession s = deckSession();
        assertThat(s.card().word()).isEqualTo("rev");
        assertThat(s.card().queue()).isEqualTo("review");
        assertThat(s.card().previews()).extracting(RatingPreview::label)
                .containsExactly("10 phút", "12 ngày", "25 ngày", "1,1 tháng");
        verify(uvRepo, never()).deckNewCardIds(any(), any(), anyInt(), any(), any());
    }

    @Test
    void thenNewCardsInTheSeededOrder() {
        learningDue(NOW);
        VocabCard fresh = card("fresh");
        when(uvRepo.deckNewCardIds(userId, deck.getId(), 0, NO_GROUP, "seed1")).thenReturn(List.of(fresh.getId()));
        when(cardRepo.findById(fresh.getId())).thenReturn(Optional.of(fresh));

        StudySession s = deckSession();
        assertThat(s.card().cardId()).isEqualTo(fresh.getId());
        assertThat(s.card().userVocabId()).isNull();
        assertThat(s.card().state()).isEqualTo("NEW");
        assertThat(s.card().queue()).isEqualTo("new");
        assertThat(s.card().previews()).extracting(RatingPreview::label)
                .containsExactly("<1 phút", "6 phút", "10 phút", "4 ngày");
        assertThat(s.nextDueAt()).isNull();
    }

    @Test
    void learnAheadOnlyWhenNothingElseIsLeft() {
        learningDue(NOW);
        UserVocab soon = row(card("soon"), CardState.LEARNING, 0, NOW.plusMinutes(8));
        learningDue(NOW.plusMinutes(20), soon);

        StudySession s = deckSession();
        assertThat(s.card().word()).isEqualTo("soon");
        verify(uvRepo).deckNewCardIds(any(), any(), anyInt(), any(), any());
    }

    @Test
    void doneForTodayCarriesTheNextDueTime() {
        learningDue(NOW);
        learningDue(NOW.plusMinutes(20));
        OffsetDateTime tomorrow = CUTOFF;
        when(uvRepo.deckNextDue(userId, deck.getId(), 0, NO_GROUP)).thenReturn(tomorrow);

        StudySession s = deckSession();
        assertThat(s.card()).isNull();
        assertThat(s.nextDueAt()).isEqualTo(tomorrow);
    }

    @Test
    void groupFilterIsPassedToTheQueries() {
        UUID group = UUID.randomUUID();
        service.deckSession(principal, deck.getId(), group.toString(), "s");
        verify(uvRepo).deckStudyCounts(eq(userId), eq(deck.getId()), eq(1), eq(group), any(), any());
        service.deckSession(principal, deck.getId(), "none", "s");
        verify(uvRepo).deckStudyCounts(eq(userId), eq(deck.getId()), eq(2), eq(NO_GROUP), any(), any());
        assertThatThrownBy(() -> service.deckSession(principal, deck.getId(), "bad", "s"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void globalSessionUsesTheUsersRowsAndOrderAdded() {
        when(uvRepo.globalStudyCounts(eq(userId), any(), any())).thenReturn(List.<Object[]>of(new Object[]{0L, 0L, 1L}));
        UserVocab notebookWord = row(card("serendipity"), CardState.NEW, 0, NOW.minusDays(2));
        when(uvRepo.globalNew(eq(userId), any())).thenReturn(List.of(notebookWord));

        StudySession s = service.globalSession(userId, null);
        assertThat(s.counts().newCount()).isEqualTo(1);
        assertThat(s.card().word()).isEqualTo("serendipity");
        assertThat(s.card().userVocabId()).isEqualTo(notebookWord.getId());
        assertThat(s.card().queue()).isEqualTo("new");
    }

    @Test
    void proDeckIs403ForFreeUsersAndForeignPersonalDeckIs404() {
        deck.setIsProOnly(true);
        assertThatThrownBy(this::deckSession)
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        when(premium.isPremium(userId.toString())).thenReturn(true);
        assertThat(deckSession().counts()).isNotNull();

        VocabDeck foreign = VocabDeck.builder().id(UUID.randomUUID()).name("x").ownerId(UUID.randomUUID()).build();
        when(deckRepo.findById(foreign.getId())).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.deckSession(principal, foreign.getId(), null, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.deckSession(null, deck.getId(), null, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void gradingANeverStudiedCardCreatesItsRowAndMatchesThePreview() {
        VocabCard fresh = card("fresh");
        UserVocab created = row(fresh, CardState.NEW, 0, NOW);
        when(cardRepo.findById(fresh.getId())).thenReturn(Optional.of(fresh));
        when(uvRepo.findByUserIdAndCardIdForUpdate(userId, fresh.getId()))
                .thenReturn(Optional.empty(), Optional.of(created));
        String preview = service.toCard(null, fresh, NOW).previews().get(2).label();

        SubmitReviewResponse r = service.grade(principal, request(null, fresh.getId(), 3));

        verify(uvRepo).insertNewIfAbsent(userId, fresh.getId(), NOW);
        assertThat(r.state()).isEqualTo("LEARNING");
        assertThat(r.step()).isEqualTo(1);
        assertThat(r.nextReviewAt()).isEqualTo(NOW.plusMinutes(10));
        assertThat(r.label()).isEqualTo(preview).isEqualTo("10 phút");
        assertThat(r.repetitions()).isEqualTo(1);
        assertThat(r.session().counts().newCount()).isEqualTo(700);
    }

    @Test
    void goodTwiceGraduatesWithOneDay() {
        VocabCard c = card("w");
        UserVocab uv = row(c, CardState.NEW, 0, NOW);
        when(uvRepo.findByIdForUpdate(uv.getId())).thenReturn(Optional.of(uv));

        service.grade(principal, request(uv.getId(), null, 3));
        SubmitReviewResponse second = service.grade(principal, request(uv.getId(), null, 3));

        assertThat(second.state()).isEqualTo("REVIEW");
        assertThat(second.intervalDays()).isEqualTo(1);
        assertThat(second.interval()).isEqualTo(1);
        assertThat(second.label()).isEqualTo("1 ngày");
        assertThat(second.nextReviewAt()).isEqualTo(CUTOFF);
    }

    @Test
    void againOnAReviewCardGoesToRelearning() {
        UserVocab uv = row(card("w"), CardState.REVIEW, 10, OffsetDateTime.parse("2026-10-15T04:00:00+07:00"));
        when(uvRepo.findByIdForUpdate(uv.getId())).thenReturn(Optional.of(uv));

        SubmitReviewResponse r = service.grade(principal, request(uv.getId(), null, 1));

        assertThat(r.state()).isEqualTo("RELEARNING");
        assertThat(r.lapses()).isEqualTo(1);
        assertThat(r.easeFactor()).isEqualTo(2.30);
        assertThat(r.label()).isEqualTo("10 phút");
        assertThat(uv.getQuality()).isEqualTo(1);
    }

    @Test
    void legacyQualityStillWorks() {
        UserVocab uv = row(card("w"), CardState.REVIEW, 10, OffsetDateTime.parse("2026-10-15T04:00:00+07:00"));
        when(uvRepo.findByIdForUpdate(uv.getId())).thenReturn(Optional.of(uv));

        SubmitReviewResponse r = service.grade(principal, new SubmitReviewRequest(uv.getId(), 4));
        assertThat(r.rating()).isEqualTo(3);
        assertThat(r.intervalDays()).isEqualTo(25);
    }

    @Test
    void gradingRules() {
        UserVocab other = row(card("w"), CardState.NEW, 0, NOW);
        other.setUserId(UUID.randomUUID());
        when(uvRepo.findByIdForUpdate(other.getId())).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.grade(principal, request(other.getId(), null, 3)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.grade(principal, request(null, null, 3)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.grade(principal, request(other.getId(), null, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        // a Pro card graded for the first time by a free user: 403, nothing written
        deck.setIsProOnly(true);
        VocabCard pro = card("pro");
        when(cardRepo.findById(pro.getId())).thenReturn(Optional.of(pro));
        when(uvRepo.findByUserIdAndCardIdForUpdate(userId, pro.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.grade(principal, globalRequest(pro.getId())))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(uvRepo, never()).insertNewIfAbsent(any(), any(), any());

        // someone else's personal card: 404
        VocabDeck foreign = VocabDeck.builder().id(UUID.randomUUID()).name("x").ownerId(UUID.randomUUID()).build();
        VocabCard foreignCard = VocabCard.builder().id(UUID.randomUUID()).deck(foreign).word("x").meaning("x").build();
        when(cardRepo.findById(foreignCard.getId())).thenReturn(Optional.of(foreignCard));
        when(uvRepo.findByUserIdAndCardIdForUpdate(userId, foreignCard.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.grade(principal, globalRequest(foreignCard.getId())))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void aProDeckScopeIsCheckedBeforeWriting() {
        deck.setIsProOnly(true);
        UserVocab uv = row(card("w"), CardState.NEW, 0, NOW);
        when(uvRepo.findByIdForUpdate(uv.getId())).thenReturn(Optional.of(uv));
        SubmitReviewRequest req = new SubmitReviewRequest(uv.getId(), null, 3, null, null, deck.getId(), null, "s");
        assertThatThrownBy(() -> service.grade(principal, req)).isInstanceOf(ApiException.class);
        verify(uvRepo, never()).saveAndFlush(any());
    }

    @Test
    void answeredAtIsOnlyTrustedWithinTenMinutes() {
        assertThat(StudyService.answeredAt(null, NOW)).isEqualTo(NOW);
        assertThat(StudyService.answeredAt(NOW.minusMinutes(3), NOW)).isEqualTo(NOW.minusMinutes(3));
        assertThat(StudyService.answeredAt(NOW.minusMinutes(11), NOW)).isEqualTo(NOW);
        assertThat(StudyService.answeredAt(NOW.plusSeconds(5), NOW)).isEqualTo(NOW);
    }

    private SubmitReviewRequest globalRequest(UUID cardId) {
        return new SubmitReviewRequest(null, cardId, 3, null, null, null, null, null);
    }

    private SubmitReviewRequest request(UUID userVocabId, UUID cardId, Integer rating) {
        return new SubmitReviewRequest(userVocabId, cardId, rating, null, null, deck.getId(), null, "seed1");
    }
}
