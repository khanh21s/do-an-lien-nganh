package com.parroto.content.dto;

import com.parroto.content.entity.Sentence;
import lombok.Builder;

import java.util.UUID;

@Builder
public record SentenceResponse(
        UUID id,
        Integer sentenceOrder,
        String text,
        String ipa,
        String translationVi,
        Integer startMs,
        Integer endMs,
        String audioUrl,
        String imageUrl
) {
    public static SentenceResponse from(Sentence s) {
        return SentenceResponse.builder()
                .id(s.getId())
                .sentenceOrder(s.getSentenceOrder())
                .text(s.getText())
                .ipa(s.getIpa())
                .translationVi(s.getTranslationVi())
                .startMs(s.getStartMs())
                .endMs(s.getEndMs())
                .audioUrl(s.getAudioUrl())
                .imageUrl(s.getImageUrl())
                .build();
    }
}
