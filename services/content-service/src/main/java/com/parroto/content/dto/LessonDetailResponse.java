package com.parroto.content.dto;

import com.parroto.content.entity.Lesson;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record LessonDetailResponse(
        UUID id,
        String title,
        String sourceUrl,
        String cefrLevel,
        String topicTag,
        String description,
        String audioUrl,
        Integer duration,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<SentenceResponse> sentences
) {
    public static LessonDetailResponse from(Lesson lesson, List<SentenceResponse> sentences) {
        return LessonDetailResponse.builder()
                .id(lesson.getId())
                .title(lesson.getTitle())
                .sourceUrl(lesson.getSourceUrl())
                .cefrLevel(lesson.getCefrLevel())
                .topicTag(lesson.getTopicTag())
                .description(lesson.getDescription())
                .audioUrl(lesson.getAudioUrl())
                .duration(lesson.getDuration())
                .createdAt(lesson.getCreatedAt())
                .updatedAt(lesson.getUpdatedAt())
                .sentences(sentences)
                .build();
    }
}
