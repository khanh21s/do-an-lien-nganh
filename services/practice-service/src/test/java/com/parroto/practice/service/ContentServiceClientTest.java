package com.parroto.practice.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContentServiceClientTest {

    private final RestTemplate rest = mock(RestTemplate.class);
    private final ContentServiceClient client = new ContentServiceClient(rest, "http://content:8002");

    @Test
    void parsesLessonWithProFlagAndSentences() {
        UUID lessonId = UUID.randomUUID();
        UUID sentenceId = UUID.randomUUID();
        when(rest.getForObject(anyString(), eq(Map.class), eq(lessonId))).thenReturn(Map.of(
                "id", lessonId.toString(), "title", "T", "audioUrl", "local:a.mp3", "isPro", true,
                "sentences", List.of(Map.of("id", sentenceId.toString(), "text", "Hi.", "sentenceOrder", 1,
                        "startMs", 0, "endMs", 800))));

        ContentServiceClient.PracticeTarget target = client.fetchSentence(lessonId, sentenceId);

        assertThat(target.lesson().isPro()).isTrue();
        assertThat(target.lesson().title()).isEqualTo("T");
        assertThat(target.sentence().text()).isEqualTo("Hi.");
        assertThat(target.sentence().endMs()).isEqualTo(800);
    }

    @Test
    void missingIsProMeansFreeAndNotFoundIsEmpty() {
        UUID lessonId = UUID.randomUUID();
        when(rest.getForObject(anyString(), eq(Map.class), eq(lessonId))).thenReturn(Map.of("id", lessonId.toString()));
        assertThat(client.fetchLesson(lessonId).isPro()).isFalse();

        UUID gone = UUID.randomUUID();
        when(rest.getForObject(anyString(), eq(Map.class), eq(gone)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "nf", null, null, null));
        assertThat(client.findLesson(gone)).isEmpty();
    }
}
