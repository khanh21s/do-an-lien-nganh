package com.parroto.vocabulary.service;

import com.parroto.vocabulary.dto.GameDtos.AnswerRequest;
import com.parroto.vocabulary.dto.GameDtos.AnswerResult;
import com.parroto.vocabulary.dto.GameDtos.Best;
import com.parroto.vocabulary.dto.GameDtos.GameState;
import com.parroto.vocabulary.dto.GameDtos.Hint;
import com.parroto.vocabulary.dto.GameDtos.Question;
import com.parroto.vocabulary.dto.GameDtos.StartGameRequest;
import com.parroto.vocabulary.dto.GameDtos.Stats;
import com.parroto.vocabulary.dto.GameDtos.Summary;
import com.parroto.vocabulary.dto.GameDtos.SummaryItem;
import com.parroto.vocabulary.entity.GameAnswer;
import com.parroto.vocabulary.entity.GameResult;
import com.parroto.vocabulary.entity.GameSession;
import com.parroto.vocabulary.entity.UserVocab;
import com.parroto.vocabulary.entity.VocabCard;
import com.parroto.vocabulary.entity.VocabDeck;
import com.parroto.vocabulary.exception.ApiException;
import com.parroto.vocabulary.repository.GameAnswerRepository;
import com.parroto.vocabulary.repository.GameSessionRepository;
import com.parroto.vocabulary.repository.UserVocabRepository;
import com.parroto.vocabulary.repository.VocabCardRepository;
import com.parroto.vocabulary.repository.VocabDeckRepository;
import com.parroto.vocabulary.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * "Dịch nhanh" game (T15): the server picks a card, shows its Vietnamese meaning, grades the
 * typed English word ({@link GameAnswerChecker}) and keeps the score ({@link GameScoring}), so
 * the client cannot forge points. A 60-second SPRINT round:
 * <ol>
 *   <li>start: a random pool (max {@value #POOL_SIZE} cards) from a deck, the notebook (due words
 *       first) or a CEFR level; older ACTIVE rounds of the user are closed.</li>
 *   <li>answer / hint: one question at a time; a missed card comes back once
 *       {@value #RETRY_GAP} questions later.</li>
 *   <li>finish: idempotent summary; words already in the notebook get an SM-2 review (a miss is
 *       always applied as Again, a hit only when the word is due, so playing never pushes a
 *       not-yet-due word further away).</li>
 * </ol>
 */
@Service
public class GameService {

    static final int POOL_SIZE = 150;
    static final int RETRY_GAP = 5;
    /** Answers sent this long after the timer ran out (network latency) are still graded. */
    static final Duration GRACE = Duration.ofSeconds(2);
    static final Set<String> LEVELS = Set.of("A1", "A2", "B1", "B2", "C1", "C2");
    private static final UUID NO_SESSION = new UUID(0, 0);

    private final GameSessionRepository sessionRepository;
    private final GameAnswerRepository answerRepository;
    private final VocabCardRepository cardRepository;
    private final VocabDeckRepository deckRepository;
    private final UserVocabRepository userVocabRepository;
    private final ReviewService reviewService;
    private final PremiumClient premiumClient;
    private final Clock clock;

    @Autowired
    public GameService(GameSessionRepository sessionRepository, GameAnswerRepository answerRepository,
                       VocabCardRepository cardRepository, VocabDeckRepository deckRepository,
                       UserVocabRepository userVocabRepository, ReviewService reviewService,
                       PremiumClient premiumClient) {
        this(sessionRepository, answerRepository, cardRepository, deckRepository, userVocabRepository,
                reviewService, premiumClient, Clock.systemUTC());
    }

    GameService(GameSessionRepository sessionRepository, GameAnswerRepository answerRepository,
                VocabCardRepository cardRepository, VocabDeckRepository deckRepository,
                UserVocabRepository userVocabRepository, ReviewService reviewService,
                PremiumClient premiumClient, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.answerRepository = answerRepository;
        this.cardRepository = cardRepository;
        this.deckRepository = deckRepository;
        this.userVocabRepository = userVocabRepository;
        this.reviewService = reviewService;
        this.premiumClient = premiumClient;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ start

    @Transactional
    public GameState start(AuthenticatedUser principal, StartGameRequest request) {
        UUID userId = principal.userUuid();
        String mode = request.mode() == null || request.mode().isBlank()
                ? GameScoring.MODE_SPRINT : request.mode().trim().toUpperCase(Locale.ROOT);
        if (!GameScoring.MODE_SPRINT.equals(mode)) {
            throw ApiException.badRequest("Chế độ chơi không hợp lệ");
        }
        String source = request.source() == null ? "" : request.source().trim().toUpperCase(Locale.ROOT);
        OffsetDateTime now = now();

        for (GameSession old : sessionRepository.findByUserIdAndStatus(userId, GameSession.STATUS_ACTIVE)) {
            closeStale(old, now);
        }

        List<UUID> pool;
        String sourceKey;
        String sourceLabel;
        switch (source) {
            case "DECK" -> {
                if (request.deckId() == null) {
                    throw ApiException.badRequest("Hãy chọn bộ từ");
                }
                VocabDeck deck = deckRepository.findById(request.deckId())
                        .filter(d -> !d.isPersonal() || d.isOwnedBy(userId))
                        .orElseThrow(() -> ApiException.notFound("Deck not found"));
                if (Boolean.TRUE.equals(deck.getIsProOnly()) && !premiumClient.isPremium(principal.userId())) {
                    throw ApiException.forbidden("Bộ từ Pro – nâng cấp Premium để chơi");
                }
                pool = cardRepository.randomIdsInDeck(deck.getId(), POOL_SIZE);
                sourceKey = "deck:" + deck.getId();
                sourceLabel = deck.isPersonal() ? "Từ tự thêm" : deck.getName();
            }
            case "NOTEBOOK" -> {
                pool = userVocabRepository.notebookCardIdsForGame(userId, POOL_SIZE);
                sourceKey = "notebook";
                sourceLabel = "Sổ tay của tôi";
                if (pool.isEmpty()) {
                    throw ApiException.badRequest("Sổ tay chưa có từ nào. Hãy thêm từ vào sổ tay trước khi chơi.");
                }
            }
            case "LEVEL" -> {
                String level = request.level() == null ? "" : request.level().trim().toUpperCase(Locale.ROOT);
                if (!LEVELS.contains(level)) {
                    throw ApiException.badRequest("Trình độ phải là A1–C2");
                }
                pool = cardRepository.randomIdsByLevel(level, premiumClient.isPremium(principal.userId()), POOL_SIZE);
                sourceKey = "level:" + level;
                sourceLabel = "Trình độ " + level;
            }
            default -> throw ApiException.badRequest("Nguồn từ phải là DECK, NOTEBOOK hoặc LEVEL");
        }
        if (pool.isEmpty()) {
            throw ApiException.badRequest("Chưa có từ nào để chơi ở nguồn này");
        }

        GameSession session = GameSession.builder()
                .userId(userId)
                .mode(mode)
                .source(source)
                .sourceKey(sourceKey)
                .sourceLabel(sourceLabel)
                .cardQueue(pool.stream().map(UUID::toString).collect(Collectors.joining(",")))
                .previousBest(sessionRepository.bestScore(userId, mode, sourceKey, NO_SESSION))
                .startedAt(now)
                .endsAt(now.plusSeconds(GameScoring.SPRINT_SECONDS))
                .build();
        session = sessionRepository.save(session);
        GameAnswer first = askNext(session, 1, now)
                .orElseThrow(() -> ApiException.badRequest("Chưa có từ nào để chơi ở nguồn này"));

        return GameState.builder()
                .sessionId(session.getId())
                .mode(mode)
                .source(source)
                .sourceLabel(sourceLabel)
                .durationSec(GameScoring.SPRINT_SECONDS)
                .remainingMs(remainingMs(session, now))
                .score(0)
                .combo(0)
                .previousBest(session.getPreviousBest())
                .question(toQuestion(first))
                .build();
    }

    // ------------------------------------------------------------------ hint

    @Transactional
    public Hint hint(UUID userId, UUID sessionId) {
        GameSession session = lockActive(userId, sessionId);
        OffsetDateTime now = now();
        if (now.isAfter(session.getEndsAt())) {
            throw ApiException.conflict("Hết giờ");
        }
        GameAnswer current = pending(session).orElseThrow(() -> ApiException.conflict("Không có câu hỏi nào"));
        if (current.getHintsUsed() < GameScoring.MAX_HINTS) {
            current.setHintsUsed(current.getHintsUsed() + 1);
            answerRepository.save(current);
        }
        return buildHint(current.getCard(), current.getHintsUsed());
    }

    static Hint buildHint(VocabCard card, int level) {
        String example = level >= 3 ? GameAnswerChecker.blankedExample(card.getExample(), card.getWord()) : null;
        return Hint.builder()
                .hintsUsed(level)
                .maxHints(GameScoring.MAX_HINTS)
                .partOfSpeech(card.getPartOfSpeech())
                .letterCount(GameAnswerChecker.letterCount(card.getWord()))
                // level 3 without a usable example sentence reveals the last letter instead
                .mask(GameAnswerChecker.mask(card.getWord(), level, level >= 3 && example == null))
                .example(example)
                .build();
    }

    // ------------------------------------------------------------------ answer

    @Transactional
    public AnswerResult answer(UUID userId, UUID sessionId, AnswerRequest request) {
        GameSession session = lockActive(userId, sessionId);
        OffsetDateTime now = now();
        if (now.isAfter(session.getEndsAt().plus(GRACE))) {
            return AnswerResult.builder()
                    .score(session.getScore())
                    .combo(session.getCombo())
                    .timeUp(true)
                    .finished(true)
                    .build();
        }
        GameAnswer current = pending(session).orElseThrow(() -> ApiException.conflict("Không có câu hỏi nào"));
        VocabCard card = current.getCard();

        boolean skip = Boolean.TRUE.equals(request.skip());
        String typed = request.answer() == null ? "" : request.answer().trim();
        if (!skip && typed.isEmpty()) {
            throw ApiException.badRequest("Hãy nhập đáp án");
        }
        GameAnswerChecker.Check check;
        if (skip) {
            check = new GameAnswerChecker.Check(GameResult.SKIPPED, null);
        } else {
            check = GameAnswerChecker.check(typed, card, List.of());
            if (check.result() != GameResult.CORRECT) {
                Set<String> keys = GameAnswerChecker.lookupKeys(typed);
                List<VocabCard> sameWord = keys.isEmpty() ? List.of() : cardRepository.findSameWordVisibleTo(keys, userId);
                if (!sameWord.isEmpty()) {
                    GameAnswerChecker.Check alt = GameAnswerChecker.check(typed, card, sameWord);
                    if (alt.result() == GameResult.CORRECT) {
                        check = alt;
                    }
                }
            }
        }

        long elapsedMs = Math.max(0, Duration.between(current.getAskedAt(), now).toMillis());
        GameScoring.Outcome outcome = GameScoring.score(check.result(), level(card), session.getCombo(),
                current.getHintsUsed(), elapsedMs);

        current.setAnsweredAt(now);
        current.setAnswer(skip ? null : truncate(typed, 255));
        current.setResult(check.result().name());
        current.setPoints(outcome.points());
        answerRepository.save(current);

        session.setScore(session.getScore() + outcome.points());
        session.setCombo(outcome.combo());
        session.setBestCombo(Math.max(session.getBestCombo(), outcome.combo()));
        switch (check.result()) {
            case CORRECT -> session.setCorrectCount(session.getCorrectCount() + 1);
            case NEAR -> session.setNearCount(session.getNearCount() + 1);
            case WRONG -> session.setWrongCount(session.getWrongCount() + 1);
            case SKIPPED -> session.setSkippedCount(session.getSkippedCount() + 1);
        }
        if (check.result().isMiss() && !Boolean.TRUE.equals(current.getIsRetry())) {
            session.setRetryQueue(appendRetry(session.getRetryQueue(), card.getId(), current.getSeq() + RETRY_GAP));
        }

        Optional<GameAnswer> next = now.isBefore(session.getEndsAt())
                ? askNext(session, current.getSeq() + 1, now) : Optional.empty();
        sessionRepository.save(session);

        return AnswerResult.builder()
                .result(check.result().name())
                .points(outcome.points())
                .speedBonus(outcome.speedBonus())
                .multiplier(outcome.multiplier())
                .combo(session.getCombo())
                .score(session.getScore())
                .correctWord(card.getWord())
                .acceptedWord(check.acceptedWord())
                .ipa(card.getIpa())
                .partOfSpeech(card.getPartOfSpeech())
                .example(card.getExample())
                .exampleVi(card.getExampleVi())
                .audioUrl(card.getAudioUrl())
                .timeUp(false)
                .finished(next.isEmpty())
                .remainingMs(remainingMs(session, now))
                .nextQuestion(next.map(GameService::toQuestion).orElse(null))
                .build();
    }

    // ------------------------------------------------------------------ finish

    @Transactional
    public Summary finish(UUID userId, UUID sessionId) {
        GameSession session = sessionRepository.findOwnForUpdate(sessionId, userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy ván chơi"));
        if (GameSession.STATUS_ABANDONED.equals(session.getStatus())) {
            throw ApiException.conflict("Ván chơi đã bị huỷ");
        }
        if (session.isActive()) {
            finalizeSession(session, now());
        }
        return summary(session);
    }

    /** Ends a round: drops the unanswered question, applies SM-2 to notebook words, stores the result. */
    void finalizeSession(GameSession session, OffsetDateTime now) {
        answerRepository.deletePending(session.getId());
        List<GameAnswer> answers = answerRepository.findAnswered(session.getId());

        // first attempt per card decides the review quality (the retry is practice, not a new grade)
        Map<UUID, Integer> quality = new LinkedHashMap<>();
        for (GameAnswer a : answers) {
            if (!Boolean.TRUE.equals(a.getIsRetry())) {
                quality.putIfAbsent(a.getCard().getId(),
                        GameScoring.srsQuality(GameResult.valueOf(a.getResult()), a.getHintsUsed()));
            }
        }
        int updated = 0;
        if (!quality.isEmpty()) {
            for (UserVocab uv : userVocabRepository.findByUserIdAndCard_IdIn(session.getUserId(), quality.keySet())) {
                int q = quality.get(uv.getCard().getId());
                boolean due = uv.getNextReviewAt() == null || !uv.getNextReviewAt().isAfter(now);
                if (q < SM2Calculator.QUALITY_HARD || due) {
                    reviewService.applyQuality(uv, q, now);
                    userVocabRepository.save(uv);
                    updated++;
                }
            }
        }
        session.setSrsUpdated(updated);
        session.setStatus(GameSession.STATUS_FINISHED);
        session.setFinishedAt(now);
        session.setCardQueue("");
        session.setRetryQueue("");
        sessionRepository.save(session);
    }

    Summary summary(GameSession session) {
        List<GameAnswer> answers = answerRepository.findAnswered(session.getId());
        Map<UUID, UUID> notebook = new HashMap<>();
        Set<UUID> cardIds = answers.stream().map(a -> a.getCard().getId()).collect(Collectors.toSet());
        if (!cardIds.isEmpty()) {
            for (UserVocab uv : userVocabRepository.findByUserIdAndCard_IdIn(session.getUserId(), cardIds)) {
                notebook.put(uv.getCard().getId(), uv.getId());
            }
        }
        List<SummaryItem> items = answers.stream().map(a -> {
            VocabCard c = a.getCard();
            return SummaryItem.builder()
                    .seq(a.getSeq())
                    .cardId(c.getId())
                    .word(c.getWord())
                    .meaning(c.getMeaning())
                    .ipa(c.getIpa())
                    .partOfSpeech(c.getPartOfSpeech())
                    .example(c.getExample())
                    .exampleVi(c.getExampleVi())
                    .audioUrl(c.getAudioUrl())
                    .result(a.getResult())
                    .answer(a.getAnswer())
                    .hintsUsed(a.getHintsUsed())
                    .points(a.getPoints())
                    .retry(Boolean.TRUE.equals(a.getIsRetry()))
                    .userVocabId(notebook.get(c.getId()))
                    .build();
        }).toList();

        int hits = session.getCorrectCount() + session.getNearCount();
        int answered = hits + session.getWrongCount() + session.getSkippedCount();
        Integer previousBest = session.getPreviousBest();
        return Summary.builder()
                .sessionId(session.getId())
                .mode(session.getMode())
                .source(session.getSource())
                .sourceLabel(session.getSourceLabel())
                .score(session.getScore())
                .correct(session.getCorrectCount())
                .near(session.getNearCount())
                .wrong(session.getWrongCount())
                .skipped(session.getSkippedCount())
                .bestCombo(session.getBestCombo())
                .accuracy(answered == 0 ? 0 : Math.round(hits * 100f / answered))
                .previousBest(previousBest)
                .newRecord(session.getScore() > 0 && (previousBest == null || session.getScore() > previousBest))
                .srsUpdated(session.getSrsUpdated())
                .finishedAt(session.getFinishedAt())
                .items(items)
                .build();
    }

    // ------------------------------------------------------------------ stats

    @Transactional(readOnly = true)
    public Stats stats(UUID userId) {
        Map<String, Best> bests = new LinkedHashMap<>();
        for (GameSession s : sessionRepository.findBests(userId)) {
            bests.putIfAbsent(s.getMode() + "|" + s.getSourceKey(), new Best(s.getMode(), s.getSourceKey(),
                    s.getSourceLabel(), s.getScore(), s.getBestCombo(), s.getFinishedAt()));
        }
        return new Stats(sessionRepository.countByUserIdAndStatus(userId, GameSession.STATUS_FINISHED),
                sessionRepository.totalScore(userId), new ArrayList<>(bests.values()));
    }

    // ------------------------------------------------------------------ helpers

    private GameSession lockActive(UUID userId, UUID sessionId) {
        GameSession session = sessionRepository.findOwnForUpdate(sessionId, userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy ván chơi"));
        if (!session.isActive()) {
            throw ApiException.conflict("Ván chơi đã kết thúc");
        }
        return session;
    }

    /** A round left open (tab closed, new round started): kept if something was answered, else dropped. */
    private void closeStale(GameSession session, OffsetDateTime now) {
        if (answerRepository.countBySessionIdAndAnsweredAtIsNotNull(session.getId()) > 0) {
            finalizeSession(session, now);
        } else {
            answerRepository.deletePending(session.getId());
            session.setStatus(GameSession.STATUS_ABANDONED);
            session.setFinishedAt(now);
            sessionRepository.save(session);
        }
    }

    private Optional<GameAnswer> pending(GameSession session) {
        return answerRepository.findPending(session.getId()).stream().findFirst();
    }

    /**
     * Picks the next card: a missed card whose turn has come, else the next fresh card, else any
     * remaining missed card. Updates the session's queues (caller saves the session).
     */
    private Optional<GameAnswer> askNext(GameSession session, int seq, OffsetDateTime now) {
        List<String[]> retries = parseRetries(session.getRetryQueue());
        List<String> fresh = splitCsv(session.getCardQueue());

        while (true) {
            UUID cardId = null;
            boolean retry = false;
            int dueIdx = -1;
            for (int i = 0; i < retries.size(); i++) {
                if (Integer.parseInt(retries.get(i)[1]) <= seq) {
                    dueIdx = i;
                    break;
                }
            }
            if (dueIdx >= 0) {
                cardId = UUID.fromString(retries.remove(dueIdx)[0]);
                retry = true;
            } else if (!fresh.isEmpty()) {
                cardId = UUID.fromString(fresh.remove(0));
            } else if (!retries.isEmpty()) {
                cardId = UUID.fromString(retries.remove(0)[0]);
                retry = true;
            }
            session.setCardQueue(String.join(",", fresh));
            session.setRetryQueue(retries.stream().map(r -> r[0] + ":" + r[1]).collect(Collectors.joining(",")));
            if (cardId == null) {
                return Optional.empty();
            }
            // a card deleted since the pool was drawn is skipped
            Optional<VocabCard> card = cardRepository.findWithDeck(cardId);
            if (card.isEmpty()) {
                continue;
            }
            GameAnswer answer = GameAnswer.builder()
                    .sessionId(session.getId())
                    .seq(seq)
                    .card(card.get())
                    .isRetry(retry)
                    .askedAt(now)
                    .build();
            return Optional.of(answerRepository.save(answer));
        }
    }

    static Question toQuestion(GameAnswer a) {
        VocabCard c = a.getCard();
        String level = level(c);
        return new Question(a.getSeq(), c.getMeaning(), level, GameScoring.basePoints(level),
                Boolean.TRUE.equals(a.getIsRetry()));
    }

    private static String level(VocabCard card) {
        return card.getDeck() == null ? null : card.getDeck().getLevel();
    }

    private long remainingMs(GameSession session, OffsetDateTime now) {
        return Math.max(0, Duration.between(now, session.getEndsAt()).toMillis());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    static String appendRetry(String queue, UUID cardId, int seq) {
        String entry = cardId + ":" + seq;
        return queue == null || queue.isBlank() ? entry : queue + "," + entry;
    }

    private static List<String[]> parseRetries(String queue) {
        List<String[]> out = new ArrayList<>();
        for (String e : splitCsv(queue)) {
            String[] parts = e.split(":");
            if (parts.length == 2) {
                out.add(parts);
            }
        }
        return out;
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(new LinkedHashSet<>(Arrays.asList(csv.split(","))));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
