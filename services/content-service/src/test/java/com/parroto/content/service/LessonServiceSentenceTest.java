package com.parroto.content.service;

import com.parroto.content.dto.CreateSentencesRequest;
import com.parroto.content.dto.SentenceInput;
import com.parroto.content.dto.SentenceResponse;
import com.parroto.content.dto.UpdateLessonRequest;
import com.parroto.content.dto.UpdateSentenceRequest;
import com.parroto.content.entity.Lesson;
import com.parroto.content.entity.Sentence;
import com.parroto.content.exception.ApiException;
import com.parroto.content.repository.LessonRepository;
import com.parroto.content.repository.SentenceRepository;
import com.parroto.content.service.storage.LessonAudioStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** ADM-03 (update/delete sentence + renumbering), LES-07 (cache eviction), ADM-04 (upload validation). */
class LessonServiceSentenceTest {

    private LessonRepository lessonRepository;
    private SentenceRepository sentenceRepository;
    private CacheService cacheService;
    private LessonAudioStorage audioStorage;
    private LessonService service;

    private UUID lessonId;
    private Lesson lesson;
    private List<Sentence> sentences;

    @BeforeEach
    void setUp() {
        lessonRepository = mock(LessonRepository.class);
        sentenceRepository = mock(SentenceRepository.class);
        cacheService = mock(CacheService.class);
        audioStorage = mock(LessonAudioStorage.class);
        service = new LessonService(lessonRepository, sentenceRepository, cacheService, audioStorage);

        lessonId = UUID.randomUUID();
        lesson = Lesson.builder().id(lessonId).title("L").isDeleted(false).audioUrl("local:seed/lesson-1/full.mp3").build();
        sentences = new ArrayList<>();
        IntStream.rangeClosed(1, 5).forEach(i -> sentences.add(Sentence.builder()
                .id(UUID.randomUUID()).lesson(lesson).sentenceOrder(i).text("s" + i)
                .startMs(i * 1000).endMs(i * 1000 + 900).build()));

        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
        when(lessonRepository.saveAndFlush(any(Lesson.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sentenceRepository.findByLessonIdOrderBySentenceOrderAsc(lessonId)).thenReturn(sentences);
        when(sentenceRepository.saveAll(anyIterable())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteSecondOfFiveRenumbersRemainingToOneThroughFour() {
        Sentence second = sentences.get(1);

        service.deleteSentence(lessonId, second.getId());

        verify(sentenceRepository).delete(second);
        ArgumentCaptor<List<Sentence>> saved = ArgumentCaptor.forClass(List.class);
        verify(sentenceRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(Sentence::getText).containsExactly("s1", "s3", "s4", "s5");
        assertThat(saved.getValue()).extracting(Sentence::getSentenceOrder).containsExactly(1, 2, 3, 4);
        assertCachesEvicted();
    }

    @Test
    void deleteSentenceOfAnotherLessonIs404() {
        assertThatThrownBy(() -> service.deleteSentence(lessonId, UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(sentenceRepository, never()).delete(any());
        verifyNoInteractions(cacheService);
    }

    @Test
    void deleteSentenceOfDeletedLessonIs404() {
        lesson.setIsDeleted(true);
        assertThatThrownBy(() -> service.deleteSentence(lessonId, sentences.get(0).getId()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateTimingIsPartialAndEvictsCache() {
        Sentence third = sentences.get(2);

        SentenceResponse res = service.updateSentence(lessonId, third.getId(),
                new UpdateSentenceRequest(null, null, null, 2950, 3800, null, null, null));

        assertThat(res.startMs()).isEqualTo(2950);
        assertThat(res.endMs()).isEqualTo(3800);
        assertThat(res.text()).isEqualTo("s3");
        assertThat(res.sentenceOrder()).isEqualTo(3);
        verify(sentenceRepository).save(third);
        assertCachesEvicted();
    }

    @Test
    void updateRejectsStartNotBeforeEndIncludingMergedValues() {
        Sentence first = sentences.get(0); // 1000..1900
        assertThatThrownBy(() -> service.updateSentence(lessonId, first.getId(),
                new UpdateSentenceRequest(null, null, null, 500, 500, null, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.updateSentence(lessonId, first.getId(),
                new UpdateSentenceRequest(null, null, null, 2000, null, null, null, null)))
                .isInstanceOf(ApiException.class);
        assertThat(first.getStartMs()).isEqualTo(1000);
        verifyNoInteractions(cacheService);
    }

    @Test
    void updateTextFieldsTrimsAndClears() {
        Sentence first = sentences.get(0);
        first.setIpa("/old/");

        SentenceResponse res = service.updateSentence(lessonId, first.getId(),
                new UpdateSentenceRequest("  Hello there ", "", "Xin chào", null, null, null, null, null));

        assertThat(res.text()).isEqualTo("Hello there");
        assertThat(res.ipa()).isNull();
        assertThat(res.translationVi()).isEqualTo("Xin chào");
        assertThatThrownBy(() -> service.updateSentence(lessonId, first.getId(),
                new UpdateSentenceRequest("  ", null, null, null, null, null, null, null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateOrderMovesSentenceAndRenumbers() {
        Sentence fifth = sentences.get(4);

        SentenceResponse res = service.updateSentence(lessonId, fifth.getId(),
                new UpdateSentenceRequest(null, null, null, null, null, 2, null, null));

        assertThat(res.sentenceOrder()).isEqualTo(2);
        ArgumentCaptor<List<Sentence>> saved = ArgumentCaptor.forClass(List.class);
        verify(sentenceRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(Sentence::getText).containsExactly("s1", "s5", "s2", "s3", "s4");
        assertThat(saved.getValue()).extracting(Sentence::getSentenceOrder).containsExactly(1, 2, 3, 4, 5);

        service.updateSentence(lessonId, fifth.getId(),
                new UpdateSentenceRequest(null, null, null, null, null, 99, null, null));
        assertThat(fifth.getSentenceOrder()).isEqualTo(5);
    }

    @Test
    void addSentencesEvictsDetailAndListCaches() {
        service.addSentences(lessonId, new CreateSentencesRequest(List.of(
                new SentenceInput("new", null, null, 9000, 9500, null, null))));

        verify(sentenceRepository).saveAll(argThat((Iterable<Sentence> it) ->
                it.iterator().next().getSentenceOrder() == 6));
        assertCachesEvicted();
    }

    @Test
    void updateLessonAppliesFieldsAndEvictsCaches() {
        var res = service.updateLesson(lessonId, new UpdateLessonRequest("New title", "desc", "travel", "B2"));

        assertThat(res.title()).isEqualTo("New title");
        assertThat(res.cefrLevel()).isEqualTo("B2");
        assertThat(res.topicTag()).isEqualTo("travel");
        assertCachesEvicted();
    }

    @Test
    void uploadRejectsNonAudioBeforeTouchingStorage() {
        MockMultipartFile exe = new MockMultipartFile("file", "x.exe", "application/octet-stream", new byte[]{1, 2});

        assertThatThrownBy(() -> service.uploadLessonAudio(lessonId, exe, null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(audioStorage);
    }

    @Test
    void uploadStoresNewAudioThenDeletesOldAndEvicts() {
        MockMultipartFile mp3 = new MockMultipartFile("file", "a.mp3", "audio/mpeg", new byte[]{1, 2});
        when(audioStorage.store(any(), anyString())).thenReturn("local:lessons/" + lessonId + "/n.mp3");

        var res = service.uploadLessonAudio(lessonId, mp3, 120);

        assertThat(res.audioUrl()).isEqualTo("local:lessons/" + lessonId + "/n.mp3");
        assertThat(res.duration()).isEqualTo(120);
        assertThat(lesson.getAudioUrl()).isEqualTo(res.audioUrl());
        InOrder order = inOrder(audioStorage);
        order.verify(audioStorage).store(mp3, "lessons/" + lessonId);
        order.verify(audioStorage).delete("local:seed/lesson-1/full.mp3");
        assertCachesEvicted();
    }

    private void assertCachesEvicted() {
        verify(cacheService).invalidateByPattern("lesson:" + lessonId);
        verify(cacheService).invalidateByPattern("lessons:list:*");
    }
}
