package com.parroto.vocabulary.dto;

import com.parroto.vocabulary.entity.UserVocab;
import com.parroto.vocabulary.entity.VocabCard;
import com.parroto.vocabulary.entity.VocabDeck;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LearnedWordResponseTest {

    @Test
    void mapsSm2ScheduleFields() {
        OffsetDateTime next = OffsetDateTime.parse("2026-10-20T08:00:00+07:00");
        VocabCard card = VocabCard.builder().id(UUID.randomUUID()).word("apple").meaning("quả táo").build();
        UserVocab uv = UserVocab.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).card(card)
                .repetitions(3).intervalDays(25).easeFactor(new BigDecimal("2.36")).nextReviewAt(next).quality(3)
                .build();

        LearnedWordResponse r = LearnedWordResponse.from(uv);

        assertThat(r.userVocabId()).isEqualTo(uv.getId());
        assertThat(r.cardId()).isEqualTo(card.getId());
        assertThat(r.nextReviewAt()).isEqualTo(next);
        assertThat(r.intervalDays()).isEqualTo(25);
        assertThat(r.easeFactor()).isEqualTo(2.36);
        assertThat(r.repetitions()).isEqualTo(3);
        assertThat(r.accuracy()).isEqualTo(100.0);
        assertThat(r.custom()).isFalse();
    }

    @Test
    void customOnlyForCardsInTheUsersOwnPersonalDeck() {
        UUID userId = UUID.randomUUID();
        VocabDeck own = VocabDeck.builder().id(UUID.randomUUID()).ownerId(userId).build();
        VocabDeck pub = VocabDeck.builder().id(UUID.randomUUID()).build();
        VocabCard ownCard = VocabCard.builder().id(UUID.randomUUID()).deck(own).word("w").meaning("m")
                .ipa("/w/").partOfSpeech("noun").example("ex").exampleVi("vd").build();
        VocabCard pubCard = VocabCard.builder().id(UUID.randomUUID()).deck(pub).word("w").meaning("m").build();

        LearnedWordResponse mine = LearnedWordResponse.from(UserVocab.builder().userId(userId).card(ownCard).build());
        LearnedWordResponse fromDeck = LearnedWordResponse.from(UserVocab.builder().userId(userId).card(pubCard).build());

        assertThat(mine.custom()).isTrue();
        assertThat(mine.ipa()).isEqualTo("/w/");
        assertThat(mine.partOfSpeech()).isEqualTo("noun");
        assertThat(mine.example()).isEqualTo("ex");
        assertThat(mine.exampleVi()).isEqualTo("vd");
        assertThat(fromDeck.custom()).isFalse();
        assertThat(fromDeck.ipa()).isNull();
    }
}
