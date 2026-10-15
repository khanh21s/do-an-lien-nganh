package com.parroto.vocabulary.service;

import com.parroto.common.exception.ApiException;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.vocabulary.dto.StudyDtos.RatingPreview;
import com.parroto.vocabulary.dto.StudyDtos.StudyCard;
import com.parroto.vocabulary.dto.StudyDtos.StudyCounts;
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
import com.parroto.vocabulary.service.srs.AnkiDay;
import com.parroto.vocabulary.service.srs.AnkiDefaults;
import com.parroto.vocabulary.service.srs.AnkiScheduler;
import com.parroto.vocabulary.service.srs.Rating;
import com.parroto.vocabulary.service.srs.SrsCard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * P2-anki study sessions, Anki style, for a deck (optionally one group) or for all the user's cards
 * ("Ôn tập"). Order of the next card:
 * <ol>
 *   <li>learning/relearning cards whose step is due now (earliest first);</li>
 *   <li>review cards due today (due day first, random within a day);</li>
 *   <li>new cards: in a deck, every card never graded (random order per {@code seed}); globally,
 *       NEW rows (notebook words, "add to notebook") in the order they were added;</li>
 *   <li>learning cards due within the learn-ahead limit (20 min) when nothing else is left.</li>
 * </ol>
 * No daily limits. When nothing is left the session carries {@code nextDueAt}.
 */
@Service
public class StudyService {

    static final String MSG_DECK_NOT_FOUND = "Deck not found";
    static final String MSG_DECK_PRO = "This vocabulary deck requires a Premium subscription";
    static final String MSG_CARD_PRO = "Bộ từ Pro – nâng cấp Premium để học";
    static final Duration ANSWERED_AT_TOLERANCE = Duration.ofMinutes(10);
    private static final PageRequest FIRST = PageRequest.of(0, 1);
    private static final UUID NO_GROUP = new UUID(0L, 0L);

    private final UserVocabRepository userVocabRepository;
    private final VocabCardRepository cardRepository;
    private final VocabDeckRepository deckRepository;
    private final PremiumClient premiumClient;
    private final AnkiScheduler scheduler;
    private final Clock clock;

    @Autowired
    public StudyService(UserVocabRepository userVocabRepository, VocabCardRepository cardRepository,
                        VocabDeckRepository deckRepository, PremiumClient premiumClient, AnkiScheduler scheduler) {
        this(userVocabRepository, cardRepository, deckRepository, premiumClient, scheduler, Clock.systemUTC());
    }

    StudyService(UserVocabRepository userVocabRepository, VocabCardRepository cardRepository,
                 VocabDeckRepository deckRepository, PremiumClient premiumClient, AnkiScheduler scheduler, Clock clock) {
        this.userVocabRepository = userVocabRepository;
        this.cardRepository = cardRepository;
        this.deckRepository = deckRepository;
        this.premiumClient = premiumClient;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    /** Session scope: {@code deckId == null} = all of the user's cards. */
    record Scope(UUID deckId, int groupMode, UUID groupId) {
        static final Scope GLOBAL = new Scope(null, 0, NO_GROUP);

        boolean global() {
            return deckId == null;
        }
    }

    // ------------------------------------------------------------------ sessions

    /** {@code GET /vocabs/decks/{id}/study}: same visibility rules as the deck's cards (404 / 403 Pro). */
    @Transactional(readOnly = true)
    public StudySession deckSession(AuthenticatedUser principal, UUID deckId, String groupId, String seed) {
        if (principal == null) throw ApiException.unauthorized("Authentication required");
        Scope scope = deckScope(principal, deckId, groupId);
        return session(principal.userUuid(), scope, seed, now());
    }

    /** {@code GET /vocabs/user/study}: every card of the user, all decks. */
    @Transactional(readOnly = true)
    public StudySession globalSession(UUID userId, String seed) {
        return session(userId, Scope.GLOBAL, seed, now());
    }

    StudySession session(UUID userId, Scope scope, String rawSeed, OffsetDateTime now) {
        String seed = rawSeed == null || rawSeed.isBlank() ? UUID.randomUUID().toString() : rawSeed.trim();
        OffsetDateTime learnUntil = now.plus(AnkiDefaults.LEARN_AHEAD);
        OffsetDateTime dayCutoff = AnkiDay.nextDayStart(now);

        StudyCounts counts = counts(userId, scope, learnUntil, dayCutoff);
        StudyCard card = nextCard(userId, scope, seed, now, learnUntil, dayCutoff);
        OffsetDateTime nextDueAt = null;
        if (card == null) {
            nextDueAt = scope.global()
                    ? userVocabRepository.globalNextDue(userId)
                    : userVocabRepository.deckNextDue(userId, scope.deckId(), scope.groupMode(), scope.groupId());
        }
        return new StudySession(counts, card, nextDueAt, now);
    }

    private StudyCounts counts(UUID userId, Scope scope, OffsetDateTime learnUntil, OffsetDateTime dayCutoff) {
        List<Object[]> rows = scope.global()
                ? userVocabRepository.globalStudyCounts(userId, learnUntil, dayCutoff)
                : userVocabRepository.deckStudyCounts(userId, scope.deckId(), scope.groupMode(), scope.groupId(),
                learnUntil, dayCutoff);
        if (rows == null || rows.isEmpty() || rows.get(0) == null) return new StudyCounts(0, 0, 0);
        Object[] r = rows.get(0);
        return new StudyCounts(num(r[2]), num(r[0]), num(r[1]));
    }

    private StudyCard nextCard(UUID userId, Scope scope, String seed, OffsetDateTime now,
                               OffsetDateTime learnUntil, OffsetDateTime dayCutoff) {
        // 1) learning cards whose step is due now
        Optional<UserVocab> uv = first(learning(userId, scope, now));
        if (uv.isPresent()) return toCard(uv.get(), uv.get().getCard(), now);
        // 2) review cards due today
        uv = first(scope.global()
                ? userVocabRepository.globalReviewDue(userId, dayCutoff, seed)
                : userVocabRepository.deckReviewDue(userId, scope.deckId(), scope.groupMode(), scope.groupId(),
                dayCutoff, seed));
        if (uv.isPresent()) return toCard(uv.get(), uv.get().getCard(), now);
        // 3) new cards
        if (scope.global()) {
            uv = first(userVocabRepository.globalNew(userId, FIRST));
            if (uv.isPresent()) return toCard(uv.get(), uv.get().getCard(), now);
        } else {
            Optional<UUID> cardId = first(userVocabRepository.deckNewCardIds(userId, scope.deckId(),
                    scope.groupMode(), scope.groupId(), seed));
            if (cardId.isPresent()) {
                Optional<VocabCard> card = cardRepository.findById(cardId.get());
                if (card.isPresent()) {
                    UserVocab row = userVocabRepository.findByUserIdAndCard_Id(userId, cardId.get()).orElse(null);
                    return toCard(row, card.get(), now);
                }
            }
        }
        // 4) learn ahead: a learning card due within the limit, when nothing else is left
        uv = first(learning(userId, scope, learnUntil));
        return uv.map(v -> toCard(v, v.getCard(), now)).orElse(null);
    }

    private List<UserVocab> learning(UUID userId, Scope scope, OffsetDateTime until) {
        return scope.global()
                ? userVocabRepository.globalLearningDue(userId, until, FIRST)
                : userVocabRepository.deckLearningDue(userId, scope.deckId(), scope.groupMode(), scope.groupId(),
                until, FIRST);
    }

    /** The card payload with the four previews computed by the scheduler at {@code now}. */
    StudyCard toCard(UserVocab uv, VocabCard card, OffsetDateTime now) {
        SrsCard srs = uv == null ? SrsCard.newCard() : SrsCard.of(uv);
        List<RatingPreview> previews = scheduler.previews(srs, now).stream()
                .map(o -> new RatingPreview(o.rating().value, o.label(), o.card().state().name(),
                        o.card().intervalDays(), o.card().due()))
                .toList();
        return StudyCard.builder()
                .userVocabId(uv == null ? null : uv.getId())
                .cardId(card.getId())
                .deckId(card.getDeck() == null ? null : card.getDeck().getId())
                .groupId(card.getGroupId())
                .word(card.getWord())
                .meaning(card.getMeaning())
                .example(card.getExample())
                .exampleVi(card.getExampleVi())
                .ipa(card.getIpa())
                .partOfSpeech(card.getPartOfSpeech())
                .audioUrl(card.getAudioUrl())
                .imageUrl(card.getImageUrl())
                .state(srs.state().name())
                .queue(queueOf(srs.state()))
                .intervalDays(srs.intervalDays())
                .easeFactor(srs.ease().doubleValue())
                .lapses(srs.lapses())
                .repetitions(uv == null || uv.getRepetitions() == null ? 0 : uv.getRepetitions())
                .nextReviewAt(uv == null ? null : uv.getNextReviewAt())
                .previews(previews)
                .build();
    }

    static String queueOf(CardState state) {
        return switch (state) {
            case NEW -> "new";
            case LEARNING, RELEARNING -> "learning";
            case REVIEW -> "review";
        };
    }

    // ------------------------------------------------------------------ grading

    /**
     * {@code POST /vocabs/user/review}: grades one card (creating the row of a deck card graded for
     * the first time) and returns its new schedule plus the refreshed session of the given scope.
     */
    @Transactional
    public SubmitReviewResponse grade(AuthenticatedUser principal, SubmitReviewRequest request) {
        UUID userId = principal.userUuid();
        Rating rating = ratingOf(request);
        OffsetDateTime now = now();
        OffsetDateTime answeredAt = answeredAt(request.answeredAt(), now);
        // Check the scope first: a Pro/foreign deck scope fails before anything is written.
        Scope scope = request.deckId() == null ? Scope.GLOBAL : deckScope(principal, request.deckId(), request.groupId());

        UserVocab uv = locate(userId, request, now);
        AnkiScheduler.Outcome outcome = scheduler.grade(uv, rating, answeredAt);
        uv = userVocabRepository.saveAndFlush(uv);

        StudySession session = session(userId, scope, request.seed(), now);
        return SubmitReviewResponse.builder()
                .userVocabId(uv.getId())
                .cardId(uv.getCard().getId())
                .rating(rating.value)
                .state(uv.getState().name())
                .step(uv.getStep())
                .intervalDays(uv.getIntervalDays())
                .interval(uv.getIntervalDays())
                .easeFactor(uv.getEaseFactor().doubleValue())
                .repetitions(uv.getRepetitions())
                .lapses(uv.getLapses())
                .nextReviewAt(uv.getNextReviewAt())
                .label(outcome.label())
                .message(messageFor(rating, outcome.label()))
                .session(session)
                .build();
    }

    static Rating ratingOf(SubmitReviewRequest request) {
        if (request.rating() != null) return Rating.of(request.rating());
        if (request.quality() != null) return Rating.fromLegacyQuality(request.quality());
        throw ApiException.badRequest("Thiếu mức đánh giá (rating 1–4)");
    }

    static OffsetDateTime answeredAt(OffsetDateTime clientTime, OffsetDateTime now) {
        if (clientTime == null || clientTime.isAfter(now) || clientTime.isBefore(now.minus(ANSWERED_AT_TOLERANCE))) {
            return now;
        }
        return clientTime;
    }

    private UserVocab locate(UUID userId, SubmitReviewRequest request, OffsetDateTime now) {
        if (request.userVocabId() != null) {
            return userVocabRepository.findByIdForUpdate(request.userVocabId())
                    .filter(uv -> userId.equals(uv.getUserId()))
                    .orElseThrow(() -> ApiException.notFound("Vocabulary entry not found"));
        }
        if (request.cardId() == null) {
            throw ApiException.badRequest("Thiếu userVocabId hoặc cardId");
        }
        Optional<UserVocab> existing = userVocabRepository.findByUserIdAndCardIdForUpdate(userId, request.cardId());
        if (existing.isPresent()) return existing.get();

        VocabCard card = cardRepository.findById(request.cardId())
                // another user's personal card is treated as non-existent
                .filter(c -> c.getDeck() == null || !c.getDeck().isPersonal() || c.getDeck().isOwnedBy(userId))
                .orElseThrow(() -> ApiException.notFound("Card not found"));
        if (card.getDeck() != null && Boolean.TRUE.equals(card.getDeck().getIsProOnly())
                && !premiumClient.isPremium(userId.toString())) {
            throw ApiException.forbidden(MSG_CARD_PRO);
        }
        userVocabRepository.insertNewIfAbsent(userId, card.getId(), now);
        return userVocabRepository.findByUserIdAndCardIdForUpdate(userId, card.getId())
                .orElseThrow(() -> new IllegalStateException("user_vocab row missing after insert"));
    }

    static String messageFor(Rating rating, String label) {
        return rating == Rating.AGAIN
                ? "Đừng lo! Thẻ sẽ quay lại sau %s.".formatted(label)
                : "Tốt lắm! Ôn lại sau %s.".formatted(label);
    }

    // ------------------------------------------------------------------ helpers

    private Scope deckScope(AuthenticatedUser principal, UUID deckId, String groupId) {
        DeckService.GroupFilter group = DeckService.GroupFilter.parse(groupId);
        VocabDeck deck = deckRepository.findById(deckId).orElseThrow(() -> ApiException.notFound(MSG_DECK_NOT_FOUND));
        if (deck.isPersonal() && !deck.isOwnedBy(principal.userUuid())) {
            throw ApiException.notFound(MSG_DECK_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(deck.getIsProOnly()) && !premiumClient.isPremium(principal.userId())) {
            throw ApiException.forbidden(MSG_DECK_PRO);
        }
        if (group == null) return new Scope(deckId, 0, NO_GROUP);
        if (group.ungrouped()) return new Scope(deckId, 2, NO_GROUP);
        return new Scope(deckId, 1, group.id());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static <T> Optional<T> first(List<T> list) {
        return list == null || list.isEmpty() ? Optional.empty() : Optional.ofNullable(list.get(0));
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }
}
