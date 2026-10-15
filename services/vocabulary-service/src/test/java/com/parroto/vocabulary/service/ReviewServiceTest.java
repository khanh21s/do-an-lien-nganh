package com.parroto.vocabulary.service;

import com.parroto.vocabulary.dto.AddFromLessonRequest;
import com.parroto.vocabulary.entity.CardState;
import com.parroto.vocabulary.entity.UserVocab;
import com.parroto.vocabulary.service.srs.AnkiScheduler;
import com.parroto.vocabulary.service.srs.Rating;
import com.parroto.vocabulary.entity.VocabCard;
import com.parroto.vocabulary.entity.VocabDeck;
import com.parroto.common.exception.ApiException;
import com.parroto.vocabulary.repository.UserVocabRepository;
import com.parroto.vocabulary.repository.VocabCardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReviewServiceTest {

    private UserVocabRepository userVocabRepository;
    private VocabCardRepository cardRepository;
    private PremiumClient premiumClient;
    private ReviewService service;

    @BeforeEach
    void setUp() {
        userVocabRepository = mock(UserVocabRepository.class);
        cardRepository = mock(VocabCardRepository.class);
        premiumClient = mock(PremiumClient.class);
        service = new ReviewService(userVocabRepository, cardRepository, new AnkiScheduler(), premiumClient);
        when(userVocabRepository.save(any(UserVocab.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private VocabCard card(boolean proDeck) {
        VocabDeck deck = VocabDeck.builder().id(UUID.randomUUID()).name("d").isProOnly(proDeck).build();
        return VocabCard.builder().id(UUID.randomUUID()).deck(deck).word("apple").meaning("quả táo").build();
    }

    @Test
    void addFromLessonRejectsProDeckForFreeUser() {
        UUID userId = UUID.randomUUID();
        VocabCard card = card(true);
        when(cardRepository.findById(card.getId())).thenReturn(Optional.of(card));
        when(premiumClient.isPremium(userId.toString())).thenReturn(false);

        assertThatThrownBy(() -> service.addFromLesson(userId, new AddFromLessonRequest(card.getId(), "lesson")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(userVocabRepository, never()).save(any());
    }

    @Test
    void addFromLessonAllowsProDeckForPremiumUser() {
        UUID userId = UUID.randomUUID();
        VocabCard card = card(true);
        when(cardRepository.findById(card.getId())).thenReturn(Optional.of(card));
        when(premiumClient.isPremium(userId.toString())).thenReturn(true);

        assertThat(service.addFromLesson(userId, new AddFromLessonRequest(card.getId(), null)).status())
                .isEqualTo("new");
    }

    @Test
    void addFromLessonFreeDeckSkipsPremiumCheck() {
        UUID userId = UUID.randomUUID();
        VocabCard card = card(false);
        when(cardRepository.findById(card.getId())).thenReturn(Optional.of(card));

        service.addFromLesson(userId, new AddFromLessonRequest(card.getId(), null));
        verifyNoInteractions(premiumClient);
    }

    @Test
    void addFromLessonCreatesANewCard() {
        UUID userId = UUID.randomUUID();
        VocabCard card = card(false);
        when(cardRepository.findById(card.getId())).thenReturn(Optional.of(card));

        service.addFromLesson(userId, new AddFromLessonRequest(card.getId(), null));

        org.mockito.ArgumentCaptor<UserVocab> saved = org.mockito.ArgumentCaptor.forClass(UserVocab.class);
        verify(userVocabRepository).save(saved.capture());
        assertThat(saved.getValue().getState()).isEqualTo(CardState.NEW);
        assertThat(saved.getValue().getIntervalDays()).isZero();
        assertThat(saved.getValue().getEaseFactor()).isEqualByComparingTo("2.50");
    }

    @Test
    void gameRatingGoesThroughTheAnkiScheduler() {
        UserVocab uv = UserVocab.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).card(card(false)).build();
        OffsetDateTime now = OffsetDateTime.parse("2026-10-15T10:00:00+07:00");

        service.applyRating(uv, Rating.GOOD, now);

        assertThat(uv.getState()).isEqualTo(CardState.LEARNING);
        assertThat(uv.getNextReviewAt()).isEqualTo(now.plusMinutes(10));
        assertThat(uv.getRepetitions()).isEqualTo(1);
        assertThat(uv.getQuality()).isEqualTo(4);
    }
}
