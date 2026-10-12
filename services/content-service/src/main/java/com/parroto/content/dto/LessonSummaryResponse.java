package com.parroto.content.dto;

import com.parroto.content.entity.Lesson;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record LessonSummaryResponse(
        UUID id,
        String title,
        String sourceUrl,
        String cefrLevel,
        String topicTag,
        String description,
        Integer duration,
        Double durationMinutes,
        long sentenceCount,
        OffsetDateTime createdAt
) {
    public static LessonSummaryResponse from(Lesson lesson, long sentenceCount) {
        return LessonSummaryResponse.builder()
                .id(lesson.getId())
                .title(lesson.getTitle())
                .sourceUrl(lesson.getSourceUrl())
                .cefrLevel(lesson.getCefrLevel())
                .topicTag(lesson.getTopicTag())
                .description(lesson.getDescription())
                .duration(lesson.getDuration())
                .durationMinutes(lesson.getDuration() != null ? Math.round(lesson.getDuration() / 6.0) / 10.0 : null)
                .sentenceCount(sentenceCount)
                .createdAt(lesson.getCreatedAt())
                .build();
    }
}
