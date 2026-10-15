package com.parroto.vocabulary.service;

import com.parroto.common.dto.PageResponse;
import com.parroto.vocabulary.dto.*;
import com.parroto.vocabulary.entity.UserVocab;
import com.parroto.vocabulary.entity.VocabCard;
import com.parroto.common.exception.ApiException;
import com.parroto.vocabulary.repository.UserVocabRepository;
import com.parroto.vocabulary.repository.VocabCardRepository;
import com.parroto.vocabulary.entity.CardState;
import com.parroto.vocabulary.service.srs.AnkiDay;
import com.parroto.vocabulary.service.srs.AnkiDefaults;
import com.parroto.vocabulary.service.srs.AnkiScheduler;
import com.parroto.vocabulary.service.srs.Rating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final UserVocabRepository userVocabRepository;
    private final VocabCardRepository cardRepository;
    private final AnkiScheduler scheduler;
    private final PremiumClient premiumClient;

    public ReviewService(UserVocabRepository userVocabRepository, VocabCardRepository cardRepository,
                          AnkiScheduler scheduler, PremiumClient premiumClient) {
        this.userVocabRepository = userVocabRepository;
        this.cardRepository = cardRepository;
        this.scheduler = scheduler;
        this.premiumClient = premiumClient;
    }

    /**
     * Legacy {@code GET /vocabs/user/review-queue} (the review page now uses {@code GET /vocabs/user/study}):
     * learning cards due within the learn-ahead limit and review cards due today. NEW cards are not listed.
     */
    @Transactional(readOnly = true)
    public ReviewQueueResponse getReviewQueue(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now();
        List<UserVocab> due = userVocabRepository.findDueForReview(userId, now.plus(AnkiDefaults.LEARN_AHEAD),
                AnkiDay.nextDayStart(now));
        long totalCards = userVocabRepository.countByUserId(userId);
        List<ReviewQueueItem> items = due.stream().map(ReviewQueueItem::from).collect(Collectors.toList());
        return new ReviewQueueResponse(items, items.size(), totalCards);
    }

    /** Grades a stored card with the Anki scheduler (used by the "Dịch nhanh" game); the caller saves it. */
    public AnkiScheduler.Outcome applyRating(UserVocab userVocab, Rating rating, OffsetDateTime now) {
        return scheduler.grade(userVocab, rating, now);
    }

    /**
     * {@code dueToday}: learning and review cards due before the next Anki day starts (04:00 Vietnam
     * time); NEW cards are not "due". {@code bySRS}: NEW / LEARNING+RELEARNING / REVIEW.
     */
    @Transactional(readOnly = true)
    public VocabUserStatsResponse getUserStats(UUID userId) {
        OffsetDateTime cutoff = AnkiDay.nextDayStart(OffsetDateTime.now());
        long totalLearned = userVocabRepository.countByUserId(userId);
        long dueToday = userVocabRepository.countScheduledDueBefore(userId, cutoff);
        long dueThisWeek = userVocabRepository.countScheduledDueBefore(userId, cutoff.plusDays(6));
        long newCount = userVocabRepository.countByUserIdAndState(userId, CardState.NEW);
        long learning = userVocabRepository.countLearning(userId);
        long review = userVocabRepository.countByUserIdAndState(userId, CardState.REVIEW);
        Double accuracy = userVocabRepository.averageAccuracy(userId);

        return VocabUserStatsResponse.builder()
                .totalLearned(totalLearned)
                .dueToday(dueToday)
                .dueThisWeek(dueThisWeek)
                .bySRS(new VocabUserStatsResponse.BySrs(newCount, learning, review))
                .accuracy(accuracy == null ? 0.0 : Math.round(accuracy * 100.0) / 100.0)
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<LearnedWordResponse> getLearnedWords(UUID userId, String searchWord, int page, int limit) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), limit);
        Page<UserVocab> learned = userVocabRepository.findLearned(userId, blankToNull(searchWord), pageable);
        Page<LearnedWordResponse> mapped = learned.map(LearnedWordResponse::from);
        return PageResponse.of(mapped, page, limit);
    }

    @Transactional
    public AddFromLessonResponse addFromLesson(UUID userId, AddFromLessonRequest request) {
        if (userVocabRepository.findByUserIdAndCard_Id(userId, request.cardId()).isPresent()) {
            throw ApiException.conflict("Từ đã có trong sổ tay");
        }
        VocabCard card = cardRepository.findById(request.cardId())
                // another user's personal card (T13) is treated as non-existent
                .filter(c -> c.getDeck() == null || !c.getDeck().isPersonal() || c.getDeck().isOwnedBy(userId))
                .orElseThrow(() -> ApiException.notFound("Card not found"));
        if (card.getDeck() != null && Boolean.TRUE.equals(card.getDeck().getIsProOnly())
                && !premiumClient.isPremium(userId.toString())) {
            throw ApiException.forbidden("Bộ từ Pro – nâng cấp Premium để học");
        }

        // A NEW card: it shows up as "new" in the review session (P2-anki).
        UserVocab userVocab = UserVocab.builder()
                .userId(userId)
                .card(card)
                .nextReviewAt(OffsetDateTime.now())
                .build();
        userVocab = userVocabRepository.save(userVocab);

        return AddFromLessonResponse.builder()
                .userVocabId(userVocab.getId())
                .cardId(card.getId())
                .word(card.getWord())
                .meaning(card.getMeaning())
                .nextReviewAt(userVocab.getNextReviewAt())
                .status("new")
                .build();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
