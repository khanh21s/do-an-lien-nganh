package com.parroto.vocabulary.service;

import com.parroto.vocabulary.dto.*;
import com.parroto.vocabulary.entity.UserVocab;
import com.parroto.vocabulary.entity.VocabCard;
import com.parroto.vocabulary.exception.ApiException;
import com.parroto.vocabulary.repository.UserVocabRepository;
import com.parroto.vocabulary.repository.VocabCardRepository;
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
    private final SM2Calculator sm2Calculator;

    public ReviewService(UserVocabRepository userVocabRepository, VocabCardRepository cardRepository,
                          SM2Calculator sm2Calculator) {
        this.userVocabRepository = userVocabRepository;
        this.cardRepository = cardRepository;
        this.sm2Calculator = sm2Calculator;
    }

    @Transactional(readOnly = true)
    public ReviewQueueResponse getReviewQueue(UUID userId) {
        List<UserVocab> due = userVocabRepository.findDueForReview(userId, OffsetDateTime.now());
        long totalCards = userVocabRepository.countByUserId(userId);
        List<ReviewQueueItem> items = due.stream().map(ReviewQueueItem::from).collect(Collectors.toList());
        return new ReviewQueueResponse(items, items.size(), totalCards);
    }

    @Transactional
    public SubmitReviewResponse submitReview(UUID userId, SubmitReviewRequest request) {
        UserVocab userVocab = userVocabRepository.findById(request.userVocabId())
                .orElseThrow(() -> ApiException.notFound("Vocabulary entry not found"));
        if (!userVocab.getUserId().equals(userId)) {
            throw ApiException.notFound("Vocabulary entry not found");
        }

        SM2Calculator.Sm2Result result = sm2Calculator.calculate(
                userVocab.getRepetitions(), userVocab.getIntervalDays(), userVocab.getEaseFactor(), request.quality());

        OffsetDateTime now = OffsetDateTime.now();
        userVocab.setIntervalDays(result.interval());
        userVocab.setEaseFactor(result.easeFactor());
        userVocab.setRepetitions(result.repetitions());
        userVocab.setLastReviewAt(now);
        userVocab.setNextReviewAt(now.plusDays(result.interval()));
        userVocab.setQuality(request.quality());
        userVocab = userVocabRepository.save(userVocab);

        return SubmitReviewResponse.builder()
                .userVocabId(userVocab.getId())
                .nextReviewAt(userVocab.getNextReviewAt())
                .easeFactor(userVocab.getEaseFactor().doubleValue())
                .interval(result.interval())
                .repetitions(userVocab.getRepetitions())
                .message(messageFor(request.quality(), result.interval()))
                .build();
    }

    @Transactional(readOnly = true)
    public VocabUserStatsResponse getUserStats(UUID userId) {
        long totalLearned = userVocabRepository.countByUserId(userId);
        long dueToday = userVocabRepository.countByUserIdAndNextReviewAtLessThanEqual(userId, OffsetDateTime.now());
        long dueThisWeek = userVocabRepository.countByUserIdAndNextReviewAtLessThanEqual(userId, OffsetDateTime.now().plusDays(7));
        long newCount = userVocabRepository.countByUserIdAndRepetitions(userId, 0);
        long learning = userVocabRepository.countLearning(userId);
        long review = userVocabRepository.countReview(userId);
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
                .orElseThrow(() -> ApiException.notFound("Card not found"));

        UserVocab userVocab = UserVocab.builder()
                .userId(userId)
                .card(card)
                .nextReviewAt(OffsetDateTime.now())
                .intervalDays(1)
                .easeFactor(java.math.BigDecimal.valueOf(2.5))
                .repetitions(0)
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

    @Transactional
    public void removeFromNotebook(UUID userId, UUID userVocabId) {
        UserVocab userVocab = userVocabRepository.findById(userVocabId)
                .orElseThrow(() -> ApiException.notFound("Vocabulary entry not found"));
        if (!userVocab.getUserId().equals(userId)) {
            throw ApiException.notFound("Vocabulary entry not found");
        }
        userVocabRepository.delete(userVocab);
    }

    static String messageFor(int quality, int intervalDays) {
        if (quality < 3) {
            return "Đừng lo! Thẻ sẽ được ôn lại sau %d ngày.".formatted(intervalDays);
        }
        if (intervalDays <= 1) {
            return "Tốt! Ôn lại vào ngày mai.";
        }
        return "Tốt lắm! Ôn lại trong %d ngày.".formatted(intervalDays);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
