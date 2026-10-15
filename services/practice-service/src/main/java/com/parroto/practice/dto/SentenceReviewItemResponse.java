package com.parroto.practice.dto;

import com.parroto.practice.entity.SentenceReview;
import com.parroto.practice.service.ContentServiceClient;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * P2-A2: one due item of {@code GET /practice/review/queue}, with everything the review screen
 * needs to replay the sentence ({@code sentence} mirrors content-service's SentenceResponse).
 *
 * @param locked true when the lesson is Pro and the user is not premium (attempts would get 403)
 */
public record SentenceReviewItemResponse(
        UUID id,
        UUID lessonId,
        UUID sentenceId,
        String mode,
        int repetitions,
        int intervalDays,
        BigDecimal easeFactor,
        OffsetDateTime nextReviewAt,
        BigDecimal lastScore,
        OffsetDateTime lastReviewedAt,
        OffsetDateTime createdAt,
        String lessonTitle,
        String lessonAudioUrl,
        boolean lessonIsPro,
        boolean locked,
        ContentServiceClient.SentenceRef sentence
) {
    public static SentenceReviewItemResponse from(SentenceReview r, ContentServiceClient.LessonRef lesson,
                                                  ContentServiceClient.SentenceRef sentence, boolean locked) {
        return new SentenceReviewItemResponse(r.getId(), r.getLessonId(), r.getSentenceId(), r.getMode(),
                r.getRepetitions(), r.getIntervalDays(), r.getEaseFactor(), r.getNextReviewAt(), r.getLastScore(),
                r.getLastReviewedAt(), r.getCreatedAt(), lesson.title(), lesson.audioUrl(), lesson.isPro(), locked,
                sentence);
    }
}
