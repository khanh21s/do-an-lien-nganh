package com.parroto.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.parroto.common.cache.CacheService;
import com.parroto.content.dto.CreateLessonRequest;
import com.parroto.content.dto.LessonDetailResponse;
import com.parroto.content.dto.LessonSummaryResponse;
import com.parroto.content.dto.UpdateLessonRequest;
import com.parroto.content.entity.Lesson;
import com.parroto.content.repository.LessonRepository;
import com.parroto.content.repository.SentenceRepository;
import com.parroto.content.service.storage.LessonAudioStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** P2-A1: Pro flag on create/update, list filter + cache key, JSON field name. */
class LessonServiceProTest {

    private LessonRepository lessonRepository;
    private SentenceRepository sentenceRepository;
    private CacheService cacheService;
    private LessonService service;

    @BeforeEach
    void setUp() {
        lessonRepository = mock(LessonRepository.class);
        sentenceRepository = mock(SentenceRepository.class);
        cacheService = mock(CacheService.class);
        service = new LessonService(lessonRepository, sentenceRepository, cacheService, mock(LessonAudioStorage.class));
        when(lessonRepository.saveAndFlush(any(Lesson.class))).thenAnswer(inv -> {
            Lesson l = inv.getArgument(0);
            if (l.getId() == null) l.setId(UUID.randomUUID());
            return l;
        });
    }

    @Test
    void createDefaultsToFreeAndAcceptsPro() {
        LessonDetailResponse free = service.createLesson(
                new CreateLessonRequest("Free", null, "A1", null, null, "local:x.mp3", null, null));
        LessonDetailResponse pro = service.createLesson(
                new CreateLessonRequest("Pro", null, "A1", null, null, "local:x.mp3", null, true));

        assertThat(free.isPro()).isFalse();
        assertThat(pro.isPro()).isTrue();
    }

    @Test
    void updateTogglesProOnlyWhenProvided() {
        UUID id = UUID.randomUUID();
        Lesson lesson = Lesson.builder().id(id).title("L").audioUrl("a").isDeleted(false).isPro(false).build();
        when(lessonRepository.findById(id)).thenReturn(Optional.of(lesson));
        when(sentenceRepository.findByLessonIdOrderBySentenceOrderAsc(id)).thenReturn(List.of());

        assertThat(service.updateLesson(id, new UpdateLessonRequest(null, null, null, null, true)).isPro()).isTrue();
        // null leaves the flag unchanged
        assertThat(service.updateLesson(id, new UpdateLessonRequest("T2", null, null, null, null)).isPro()).isTrue();
        assertThat(service.updateLesson(id, new UpdateLessonRequest(null, null, null, null, false)).isPro()).isFalse();
        verify(cacheService, atLeastOnce()).invalidateByPattern("lessons:list:*");
        verify(cacheService, atLeastOnce()).invalidateByPattern("lesson:" + id);
    }

    @Test
    void listPassesFilterAndUsesSeparateCacheKey() {
        Lesson pro = Lesson.builder().id(UUID.randomUUID()).title("P").audioUrl("a").isPro(true).build();
        when(lessonRepository.search(isNull(), isNull(), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pro)));
        when(lessonRepository.search(isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pro)));

        var filtered = service.listLessons(null, null, true, 1, 20);
        service.listLessons(null, null, null, 1, 20);

        assertThat(filtered.data()).extracting(LessonSummaryResponse::isPro).containsExactly(true);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(cacheService, times(2)).set(keys.capture(), any(), any());
        // unfiltered key keeps its pre-P2 format, both stay under the evicted lessons:list:* pattern
        assertThat(keys.getAllValues()).containsExactly("lessons:list:null:null:1:20:pro=true", "lessons:list:null:null:1:20");
        verify(cacheService, never()).invalidateByPattern(anyString());
    }

    @Test
    void jsonFieldIsNamedIsPro() throws Exception {
        Lesson lesson = Lesson.builder().id(UUID.randomUUID()).title("P").audioUrl("a").isPro(true).build();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        JsonNode detail = mapper.valueToTree(LessonDetailResponse.from(lesson, List.of()));
        JsonNode summary = mapper.valueToTree(LessonSummaryResponse.from(lesson, 0));

        assertThat(detail.get("isPro").asBoolean()).isTrue();
        assertThat(summary.get("isPro").asBoolean()).isTrue();
        // round trip (Redis cache uses JSON)
        assertThat(mapper.treeToValue(detail, LessonDetailResponse.class).isPro()).isTrue();
    }
}
