package com.parroto.vocabulary.dto;

import com.parroto.vocabulary.entity.VocabCard;
import lombok.Builder;

import java.util.UUID;

@Builder
public record CardResponse(
        UUID id,
        String word,
        String meaning,
        String example,
        String exampleVi,
        String ipa,
        String audioUrl,
        String imageUrl,
        String partOfSpeech
) {
    public static CardResponse from(VocabCard card) {
        return CardResponse.builder()
                .id(card.getId())
                .word(card.getWord())
                .meaning(card.getMeaning())
                .example(card.getExample())
                .exampleVi(card.getExampleVi())
                .ipa(card.getIpa())
                .audioUrl(card.getAudioUrl())
                .imageUrl(card.getImageUrl())
                .partOfSpeech(card.getPartOfSpeech())
                .build();
    }
}
