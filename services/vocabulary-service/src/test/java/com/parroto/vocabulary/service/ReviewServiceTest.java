package com.parroto.vocabulary.service;

import com.parroto.vocabulary.dto.AddFromLessonRequest;
import com.parroto.vocabulary.dto.SubmitReviewRequest;
import com.parroto.vocabulary.dto.SubmitReviewResponse;
import com.parroto.vocabulary.entity.UserVocab;
import com.parroto.vocabulary.entity.VocabCard;
import com.parroto.vocabulary.entity.VocabDeck;
import com.parroto.common.exception.ApiException;
import com.parroto.vocabulary.repository.UserVocabRepository;
import com.parroto.vocabulary.repository.VocabCardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
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
        service = new ReviewService(userVocabRepository, cardRepository, new SM2Calculator(), premiumClient);
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
    void submitAgainResetsRepetitionsAndSchedulesOneDay() {
        UUID userId = UUID.randomUUID();
        UserVocab uv = UserVocab.builder().id(UUID.randomUUID()).userId(userId).card(card(false))
                .repetitions(3).intervalDays(10).easeFactor(new BigDecimal("2.50")).build();
        when(userVocabRepository.findById(uv.getId())).thenReturn(Optional.of(uv));

        OffsetDateTime before = OffsetDateTime.now();
        SubmitReviewResponse r = service.submitReview(userId, new SubmitReviewRequest(uv.getId(), 1));

        assertThat(r.repetitions()).isZero();
        assertThat(r.interval()).isEqualTo(1);
        assertThat(r.easeFactor()).isEqualTo(1.96);
        assertThat(uv.getNextReviewAt()).isAfterOrEqualTo(before.plusDays(1));
        assertThat(uv.getNextReviewAt()).isBefore(before.plusDays(1).plusMinutes(1));
        assertThat(uv.getQuality()).isEqualTo(1);
    }

    @Test
    void submitGoodOnMatureCard() {
        UUID userId = UUID.randomUUID();
        UserVocab uv = UserVocab.builder().id(UUID.randomUUID()).userId(userId).card(card(false))
                .repetitions(3).intervalDays(10).easeFactor(new BigDecimal("2.50")).build();
        when(userVocabRepository.findById(uv.getId())).thenReturn(Optional.of(uv));

        SubmitReviewResponse r = service.submitReview(userId, new SubmitReviewRequest(uv.getId(), 4));

        assertThat(r.repetitions()).isEqualTo(4);
        assertThat(r.interval()).isEqualTo(25);
        assertThat(uv.getIntervalDays()).isEqualTo(25);
    }

    @Test
    void submitReviewOfOtherUsersCardIs404() {
        UserVocab uv = UserVocab.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).card(card(false)).build();
        when(userVocabRepository.findById(uv.getId())).thenReturn(Optional.of(uv));

        assertThatThrownBy(() -> service.submitReview(UUID.randomUUID(), new SubmitReviewRequest(uv.getId(), 4)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
