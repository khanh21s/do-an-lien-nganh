package com.parroto.vocabulary.service;

import com.parroto.vocabulary.entity.CardState;
import com.parroto.vocabulary.service.srs.AnkiScheduler;

import com.parroto.vocabulary.dto.GameDtos.AnswerRequest;
import com.parroto.vocabulary.dto.GameDtos.AnswerResult;
import com.parroto.vocabulary.dto.GameDtos.GameState;
import com.parroto.vocabulary.dto.GameDtos.Progress;
import com.parroto.vocabulary.dto.GameDtos.Hint;
import com.parroto.vocabulary.dto.GameDtos.StartGameRequest;
import com.parroto.vocabulary.dto.GameDtos.Summary;
import com.parroto.vocabulary.entity.DailyChallenge;
import com.parroto.vocabulary.entity.GameAnswer;
import com.parroto.vocabulary.entity.GameSession;
import com.parroto.vocabulary.entity.UserVocab;
import com.parroto.vocabulary.entity.VocabCard;
import com.parroto.vocabulary.entity.VocabDeck;
import com.parroto.common.exception.ApiException;
import com.parroto.vocabulary.repository.DailyChallengeRepository;
import com.parroto.vocabulary.repository.GameAnswerRepository;
import com.parroto.vocabulary.repository.GameSessionRepository;
import com.parroto.vocabulary.repository.UserVocabRepository;
import com.parroto.vocabulary.repository.VocabCardRepository;
import com.parroto.vocabulary.repository.VocabDeckRepository;
import com.parroto.common.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** GameService against in-memory repositories and a controllable clock. */
class GameServiceTest {

    /** Mutable clock: tests move time forward with {@link #advance}. */
    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-14T08:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final UUID userId = UUID.randomUUID();
    private final AuthenticatedUser principal = new AuthenticatedUser(userId.toString(), "u@test.local", "B2");
    private final TestClock clock = new TestClock();
    private final Map<UUID, GameSession> sessions = new HashMap<>();
    private final List<GameAnswer> answers = new ArrayList<>();
    private final Map<UUID, VocabCard> cards = new HashMap<>();

    private GameSessionRepository sessionRepository;
    private GameAnswerRepository answerRepository;
    private VocabCardRepository cardRepository;
    private VocabDeckRepository deckRepository;
    private UserVocabRepository userVocabRepository;
    private PremiumClient premiumClient;
    private DailyChallengeRepository dailyRepository;
    private GameProgressService progressService;
    private final Map<LocalDate, DailyChallenge> dailies = new HashMap<>();
    private GameService service;
    private VocabDeck deck;
    private List<VocabCard> deckCards;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(GameSessionRepository.class);
        answerRepository = mock(GameAnswerRepository.class);
        cardRepository = mock(VocabCardRepository.class);
        deckRepository = mock(VocabDeckRepository.class);
        userVocabRepository = mock(UserVocabRepository.class);
        premiumClient = mock(PremiumClient.class);
        dailyRepository = mock(DailyChallengeRepository.class);
        progressService = mock(GameProgressService.class);
        ReviewService reviewService = new ReviewService(userVocabRepository, cardRepository, new AnkiScheduler(), premiumClient);
        service = new GameService(sessionRepository, answerRepository, cardRepository, deckRepository,
                userVocabRepository, dailyRepository, reviewService, premiumClient, progressService, clock);

        when(progressService.today()).thenReturn(LocalDate.of(2026, 10, 14));
        when(progressService.progress(any())).thenReturn(Progress.builder().xp(0).level(1).levelXpNeeded(100)
                .dailyGoal(200).build());
        when(dailyRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(dailies.get(inv.getArgument(0))));
        when(dailyRepository.insertIfAbsent(any(), any())).thenAnswer(inv -> {
            dailies.putIfAbsent(inv.getArgument(0), new DailyChallenge(inv.getArgument(0), inv.getArgument(1)));
            return 1;
        });
        when(sessionRepository.findByUserIdAndModeAndSourceKey(any(), any(), any())).thenAnswer(inv -> sessions.values().stream()
                .filter(s -> s.getUserId().equals(inv.getArgument(0)) && s.getMode().equals(inv.getArgument(1))
                        && s.getSourceKey().equals(inv.getArgument(2))).findFirst());

        when(sessionRepository.save(any(GameSession.class))).thenAnswer(inv -> {
            GameSession s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            sessions.put(s.getId(), s);
            return s;
        });
        when(sessionRepository.findOwnForUpdate(any(), any())).thenAnswer(inv -> Optional.ofNullable(sessions.get(inv.getArgument(0)))
                .filter(s -> s.getUserId().equals(inv.getArgument(1))));
        when(sessionRepository.findByUserIdAndStatus(any(), any())).thenAnswer(inv -> sessions.values().stream()
                .filter(s -> s.getUserId().equals(inv.getArgument(0)) && s.getStatus().equals(inv.getArgument(1))).toList());
        when(answerRepository.save(any(GameAnswer.class))).thenAnswer(inv -> {
            GameAnswer a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
                answers.add(a);
            }
            return a;
        });
        when(answerRepository.findPending(any())).thenAnswer(inv -> answers.stream()
                .filter(a -> a.getSessionId().equals(inv.getArgument(0)) && a.getAnsweredAt() == null)
                .sorted(Comparator.comparing(GameAnswer::getSeq).reversed()).toList());
        when(answerRepository.findAnswered(any())).thenAnswer(inv -> answers.stream()
                .filter(a -> a.getSessionId().equals(inv.getArgument(0)) && a.getAnsweredAt() != null)
                .sorted(Comparator.comparing(GameAnswer::getSeq)).toList());
        when(answerRepository.countBySessionIdAndAnsweredAtIsNotNull(any())).thenAnswer(inv -> answers.stream()
                .filter(a -> a.getSessionId().equals(inv.getArgument(0)) && a.getAnsweredAt() != null).count());
        when(answerRepository.deletePending(any())).thenAnswer(inv -> {
            int before = answers.size();
            answers.removeIf(a -> a.getSessionId().equals(inv.getArgument(0)) && a.getAnsweredAt() == null);
            return before - answers.size();
        });
        when(cardRepository.findWithDeck(any())).thenAnswer(inv -> Optional.ofNullable(cards.get(inv.getArgument(0))));
        when(cardRepository.findSameWordVisibleTo(any(), any())).thenReturn(List.of());
        when(userVocabRepository.findByUserIdAndCard_IdIn(any(), any())).thenReturn(List.of());

        deck = VocabDeck.builder().id(UUID.randomUUID()).name("B2 Essential").level("B2").isProOnly(false).build();
        when(deckRepository.findById(deck.getId())).thenReturn(Optional.of(deck));
        deckCards = new ArrayList<>();
        for (String[] w : new String[][]{{"huge", "khổng lồ"}, {"decide", "quyết định"}, {"environment", "môi trường"},
                {"borrow", "mượn"}, {"collect", "thu thập"}, {"protect", "bảo vệ"}, {"improve", "cải thiện"},
                {"arrive", "đến nơi"}, {"believe", "tin tưởng"}, {"compare", "so sánh"}, {"discover", "khám phá"},
                {"explain", "giải thích"}}) {
            VocabCard c = VocabCard.builder().id(UUID.randomUUID()).deck(deck).word(w[0]).meaning(w[1])
                    .example("I want to " + w[0] + " it.").partOfSpeech("verb").build();
            cards.put(c.getId(), c);
            deckCards.add(c);
        }
        when(cardRepository.randomIdsInDeck(eq(deck.getId()), anyInt()))
                .thenReturn(deckCards.stream().map(VocabCard::getId).toList());
        // only B2 words exist: the easier stages come back empty and are topped up
        when(cardRepository.randomIdsByLevels(any(), anyBoolean(), anyInt())).thenAnswer(inv -> {
            java.util.Collection<String> levels = inv.getArgument(0);
            int limit = inv.getArgument(2);
            return levels.contains("B2") ? deckCards.stream().map(VocabCard::getId).limit(limit).toList() : List.of();
        });
        when(cardRepository.randomPublicIds(anyBoolean(), anyInt()))
                .thenReturn(deckCards.stream().map(VocabCard::getId).toList());
    }

    private GameState startDeck() {
        return service.start(principal, new StartGameRequest(null, "deck", deck.getId(), null));
    }

    private VocabCard currentCard(UUID sessionId) {
        return answerRepository.findPending(sessionId).get(0).getCard();
    }

    private AnswerResult answer(UUID sessionId, String text) {
        return service.answer(userId, sessionId, new AnswerRequest(text, false));
    }

    @Test
    void startsASprintWithTheFirstQuestionAndNoWordLeak() {
        when(sessionRepository.bestScore(any(), any(), any(), any())).thenReturn(120);

        GameState s = startDeck();

        assertThat(s.durationSec()).isEqualTo(60);
        assertThat(s.remainingMs()).isEqualTo(60_000);
        assertThat(s.sourceLabel()).isEqualTo("B2 Essential");
        assertThat(s.previousBest()).isEqualTo(120);
        assertThat(s.question().seq()).isEqualTo(1);
        assertThat(s.question().meaning()).isEqualTo("khổng lồ");
        assertThat(s.question().basePoints()).isEqualTo(20);
        assertThat(sessions.get(s.sessionId()).getSourceKey()).isEqualTo("deck:" + deck.getId());
    }

    @Test
    void scoresFromServerTimeAndBuildsCombo() {
        GameState s = startDeck();
        clock.advance(Duration.ofSeconds(2));

        AnswerResult r = answer(s.sessionId(), "Huge");

        assertThat(r.result()).isEqualTo("CORRECT");
        assertThat(r.points()).isEqualTo(25); // 20 + speed bonus 5
        assertThat(r.combo()).isEqualTo(1);
        assertThat(r.correctWord()).isEqualTo("huge");
        assertThat(r.nextQuestion().meaning()).isEqualTo("quyết định");
        assertThat(r.finished()).isFalse();
        assertThat(r.remainingMs()).isEqualTo(58_000);

        clock.advance(Duration.ofSeconds(10));
        AnswerResult typo = answer(s.sessionId(), "decid"); // 1 letter missing -> NEAR, combo kept
        assertThat(typo.result()).isEqualTo("NEAR");
        assertThat(typo.points()).isEqualTo(10);
        assertThat(typo.combo()).isEqualTo(1);
        assertThat(typo.score()).isEqualTo(35);
    }

    @Test
    void hintsAreProgressiveAndCostPoints() {
        GameState s = startDeck();
        Hint h1 = service.hint(userId, s.sessionId());
        assertThat(h1.mask()).isEqualTo("_ _ _ _");
        assertThat(h1.partOfSpeech()).isEqualTo("verb");
        assertThat(h1.example()).isNull();
        service.hint(userId, s.sessionId());
        Hint h3 = service.hint(userId, s.sessionId());
        assertThat(h3.mask()).isEqualTo("h _ _ _");
        assertThat(h3.example()).isEqualTo("I want to _____ it.");
        assertThat(service.hint(userId, s.sessionId()).hintsUsed()).isEqualTo(3); // capped

        AnswerResult r = answer(s.sessionId(), "huge");
        assertThat(r.points()).isEqualTo(5); // 20 × 0.25, no speed bonus with hints
    }

    @Test
    void aMissedWordComesBackFiveQuestionsLater() {
        GameState s = startDeck();
        AnswerResult skip = service.answer(userId, s.sessionId(), new AnswerRequest(null, true));
        assertThat(skip.result()).isEqualTo("SKIPPED");
        assertThat(skip.combo()).isZero();

        List<String> asked = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            VocabCard c = currentCard(s.sessionId());
            asked.add(c.getWord());
            answer(s.sessionId(), c.getWord());
        }
        // questions 2..5 are fresh, question 6 is "huge" again (missed at question 1)
        assertThat(asked.subList(0, 4)).containsExactly("decide", "environment", "borrow", "collect");
        assertThat(asked.get(4)).isEqualTo("huge");
        assertThat(answers.stream().filter(a -> a.getSeq() == 6).findFirst().orElseThrow().getIsRetry()).isTrue();
    }

    @Test
    void blankAnswerIsRejectedAndLateAnswersAreNotGraded() {
        GameState s = startDeck();
        assertThatThrownBy(() -> answer(s.sessionId(), "  "))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        clock.advance(Duration.ofSeconds(61)); // within the 2 s grace: graded, but no next question
        AnswerResult inGrace = answer(s.sessionId(), "huge");
        assertThat(inGrace.result()).isEqualTo("CORRECT");
        assertThat(inGrace.finished()).isTrue();
        assertThat(inGrace.nextQuestion()).isNull();

        clock.advance(Duration.ofSeconds(5));
        AnswerResult late = answer(s.sessionId(), "decide");
        assertThat(late.timeUp()).isTrue();
        assertThat(late.result()).isNull();
        assertThat(sessions.get(s.sessionId()).getScore()).isEqualTo(inGrace.score());
    }

    @Test
    void finishSummarisesAppliesSrsAndDetectsARecord() {
        when(sessionRepository.bestScore(any(), any(), any(), any())).thenReturn(10);
        GameState s = startDeck();
        VocabCard huge = deckCards.get(0);
        VocabCard decide = deckCards.get(1);
        OffsetDateTime future = OffsetDateTime.now(clock).plusDays(3);
        UserVocab dueHuge = UserVocab.builder().id(UUID.randomUUID()).userId(userId).card(huge)
                .state(CardState.REVIEW).nextReviewAt(OffsetDateTime.now(clock).minusDays(1)).repetitions(2).intervalDays(6)
                .easeFactor(BigDecimal.valueOf(2.5)).build();
        UserVocab notDueDecide = UserVocab.builder().id(UUID.randomUUID()).userId(userId).card(decide)
                .state(CardState.REVIEW).nextReviewAt(future).repetitions(2).intervalDays(6)
                .easeFactor(BigDecimal.valueOf(2.5)).build();
        when(userVocabRepository.findByUserIdAndCard_IdIn(eq(userId), any())).thenReturn(List.of(dueHuge, notDueDecide));

        answer(s.sessionId(), "huge");           // correct, due -> Good
        answer(s.sessionId(), "wrong-word");     // decide missed -> Again even though not due
        clock.advance(Duration.ofSeconds(60));

        Summary sum = service.finish(userId, s.sessionId());

        assertThat(sum.score()).isEqualTo(25);
        assertThat(sum.correct()).isEqualTo(1);
        assertThat(sum.wrong()).isEqualTo(1);
        assertThat(sum.accuracy()).isEqualTo(50);
        assertThat(sum.newRecord()).isTrue();
        assertThat(sum.previousBest()).isEqualTo(10);
        assertThat(sum.srsUpdated()).isEqualTo(2);
        assertThat(sum.items()).hasSize(2); // the unanswered third question is dropped
        assertThat(sum.items().get(1).userVocabId()).isEqualTo(notDueDecide.getId());
        assertThat(dueHuge.getQuality()).isEqualTo(4);
        assertThat(notDueDecide.getQuality()).isEqualTo(1);
        // P2-anki: the miss is a lapse of the review card (relearning, one more lapse)
        assertThat(notDueDecide.getState()).isEqualTo(CardState.RELEARNING);
        assertThat(notDueDecide.getLapses()).isEqualTo(1);
        assertThat(dueHuge.getState()).isEqualTo(CardState.REVIEW);
        assertThat(dueHuge.getIntervalDays()).isGreaterThan(6);
        assertThat(sessions.get(s.sessionId()).getStatus()).isEqualTo(GameSession.STATUS_FINISHED);

        // idempotent: a second finish returns the same result without re-applying SM-2
        Summary again = service.finish(userId, s.sessionId());
        assertThat(again.score()).isEqualTo(25);
        verify(userVocabRepository, times(2)).save(any(UserVocab.class));
        assertThatThrownBy(() -> answer(s.sessionId(), "x"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void startingANewRoundClosesTheOldOne() {
        GameState first = startDeck();
        GameState second = startDeck();
        assertThat(sessions.get(first.sessionId()).getStatus()).isEqualTo(GameSession.STATUS_ABANDONED);

        answer(second.sessionId(), "huge");
        startDeck();
        assertThat(sessions.get(second.sessionId()).getStatus()).isEqualTo(GameSession.STATUS_FINISHED);
    }

    @Test
    void otherUsersCannotTouchARound() {
        GameState s = startDeck();
        assertThatThrownBy(() -> service.answer(UUID.randomUUID(), s.sessionId(), new AnswerRequest("huge", false)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void sourceValidation() {
        when(userVocabRepository.notebookCardIdsForGame(any(), anyInt())).thenReturn(List.of());
        assertThatThrownBy(() -> service.start(principal, new StartGameRequest(null, "NOTEBOOK", null, null)))
                .hasMessageContaining("Sổ tay chưa có từ");
        assertThatThrownBy(() -> service.start(principal, new StartGameRequest(null, "LEVEL", null, "Z9")))
                .hasMessageContaining("A1–C2");
        deck.setIsProOnly(true);
        assertThatThrownBy(this::startDeck)
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        VocabDeck someoneElses = VocabDeck.builder().id(UUID.randomUUID()).name("x").ownerId(UUID.randomUUID()).build();
        when(deckRepository.findById(someoneElses.getId())).thenReturn(Optional.of(someoneElses));
        assertThatThrownBy(() -> service.start(principal, new StartGameRequest(null, "DECK", someoneElses.getId(), null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ------------------------------------------------------------------ T16

    private GameState startMode(String mode) {
        return service.start(principal, new StartGameRequest(mode, null, null, null));
    }

    @Test
    void survivalIsPremiumAndEndsWhenTheLivesRunOut() {
        assertThatThrownBy(() -> startMode("SURVIVAL"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        when(premiumClient.isPremium(userId.toString())).thenReturn(true);

        GameState s = startMode("survival");
        assertThat(s.lives()).isEqualTo(3);
        assertThat(s.durationSec()).isZero();
        assertThat(s.question().timeLeftMs()).isEqualTo(15_000);
        assertThat(sessions.get(s.sessionId()).getRanked()).isTrue();
        assertThat(sessions.get(s.sessionId()).getDisplayName()).isEqualTo("u***");

        answer(s.sessionId(), currentCard(s.sessionId()).getWord());
        AnswerResult miss1 = answer(s.sessionId(), "nope");
        assertThat(miss1.lives()).isEqualTo(2);
        // too slow: graded WRONG even though the word is right
        clock.advance(Duration.ofSeconds(18));
        AnswerResult slow = answer(s.sessionId(), currentCard(s.sessionId()).getWord());
        assertThat(slow.timedOut()).isTrue();
        assertThat(slow.result()).isEqualTo("WRONG");
        assertThat(slow.lives()).isEqualTo(1);
        AnswerResult last = service.answer(userId, s.sessionId(), new AnswerRequest(null, true));
        assertThat(last.lives()).isZero();
        assertThat(last.finished()).isTrue();
        assertThat(last.nextQuestion()).isNull();
        // no retries in survival
        assertThat(answers.stream().noneMatch(GameAnswer::getIsRetry)).isTrue();
    }

    @Test
    void dailyChallengeIsTheSameForEveryoneAndPlayedOnce() {
        GameState s = startMode("DAILY");
        assertThat(s.questionCount()).isEqualTo(10);
        assertThat(s.question().timeLeftMs()).isEqualTo(20_000);
        assertThat(s.sourceLabel()).isEqualTo("Thử thách 14/10");
        String words = dailies.get(LocalDate.of(2026, 10, 14)).getCardIds();
        assertThat(words.split(",")).hasSize(10);

        // interrupted: starting again resumes the same question, its timer keeps running
        clock.advance(Duration.ofSeconds(5));
        GameState resumed = startMode("DAILY");
        assertThat(resumed.sessionId()).isEqualTo(s.sessionId());
        assertThat(resumed.question().seq()).isEqualTo(1);
        assertThat(resumed.question().timeLeftMs()).isEqualTo(15_000);

        AnswerResult r = null;
        for (int i = 0; i < 10; i++) {
            r = answer(s.sessionId(), currentCard(s.sessionId()).getWord());
        }
        assertThat(r.finished()).isTrue();
        Summary sum = service.finish(userId, s.sessionId());
        assertThat(sum.correct()).isEqualTo(10);
        verify(progressService).onRoundFinished(any(), any());

        assertThatThrownBy(() -> startMode("DAILY"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        // another player gets the same words in the same order
        UUID other = UUID.randomUUID();
        GameState o = service.start(new AuthenticatedUser(other.toString(), "other@test.local", "B1"),
                new StartGameRequest("DAILY", null, null, null));
        assertThat(currentCard(o.sessionId()).getId().toString()).isEqualTo(words.split(",")[0]);
    }

    @Test
    void startingAnotherModeKeepsTodaysDailyResumable() {
        GameState daily = startMode("DAILY");
        startDeck();
        assertThat(sessions.get(daily.sessionId()).isActive()).isTrue();
        assertThat(startMode("DAILY").sessionId()).isEqualTo(daily.sessionId());
    }

    @Test
    void sprintRoundsFromTheNotebookAreNotRanked() {
        when(userVocabRepository.notebookCardIdsForGame(any(), anyInt())).thenReturn(List.of(deckCards.get(0).getId()));
        GameState nb = service.start(principal, new StartGameRequest(null, "NOTEBOOK", null, null));
        assertThat(sessions.get(nb.sessionId()).getRanked()).isFalse();
        GameState d = startDeck();
        assertThat(sessions.get(d.sessionId()).getRanked()).isTrue();
        assertThatThrownBy(() -> startMode("CHESS")).hasMessageContaining("SPRINT, SURVIVAL hoặc DAILY");
    }
}
