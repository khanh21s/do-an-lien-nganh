package com.parroto.practice.service;

import com.parroto.common.dto.PageResponse;
import com.parroto.practice.dto.*;
import com.parroto.practice.entity.PracticeAttempt;
import com.parroto.common.exception.ApiException;
import com.parroto.practice.repository.PracticeAttemptRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AttemptQueryService {

    private final PracticeAttemptRepository attemptRepository;

    public AttemptQueryService(PracticeAttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AttemptSummaryResponse> listAttempts(UUID userId, UUID lessonId, String mode, int page, int limit) {
        page = Math.max(page, 1);
        limit = Math.min(Math.max(limit, 1), 100);
        Pageable pageable = PageRequest.of(page - 1, limit); // ordering (createdAt DESC) is part of the query
        Page<PracticeAttempt> attempts = attemptRepository.search(userId, lessonId, mode, pageable);
        Page<AttemptSummaryResponse> mapped = attempts.map(AttemptSummaryResponse::from);
        return PageResponse.of(mapped, page, limit);
    }

    @Transactional(readOnly = true)
    public AttemptDetailResponse getAttempt(UUID userId, UUID attemptId) {
        PracticeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> ApiException.notFound("Attempt not found"));
        return AttemptDetailResponse.from(attempt);
    }

    @Transactional(readOnly = true)
    public LessonAttemptsResponse getLessonAttempts(UUID userId, UUID lessonId) {
        List<PracticeAttempt> attempts = attemptRepository.findByUserIdAndLessonIdOrderByCreatedAtDesc(userId, lessonId);
        List<AttemptSummaryResponse> summaries = attempts.stream().map(AttemptSummaryResponse::from).collect(Collectors.toList());
        double avgAccuracy = attempts.stream()
                .filter(a -> a.getAccuracy() != null)
                .mapToDouble(a -> a.getAccuracy().doubleValue())
                .average().orElse(0.0);
        return LessonAttemptsResponse.builder()
                .attempts(summaries)
                .summary(new LessonAttemptsResponse.Summary(avgAccuracy, attempts.size()))
                .build();
    }
}
