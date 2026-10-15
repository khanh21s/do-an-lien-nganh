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
        String partOfSpeech,
        /** user_vocab.id when the requesting user already learned this card, else null (VOC-04). */
        UUID userVocabId,
        /** P2-C: group inside the deck, null = ungrouped. */
        UUID groupId
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
                .userVocabId(null)
                .groupId(card.getGroupId())
                .build();
    }

    public CardResponse withUserVocabId(UUID newUserVocabId) {
        return new CardResponse(id, word, meaning, example, exampleVi, ipa, audioUrl, imageUrl, partOfSpeech,
                newUserVocabId, groupId);
    }
}
