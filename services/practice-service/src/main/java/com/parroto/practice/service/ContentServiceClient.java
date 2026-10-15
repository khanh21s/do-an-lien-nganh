package com.parroto.practice.service;

import com.parroto.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Fetches the reference sentence text from content-service (over the internal Docker
 * network) and validates that a lesson/sentence referenced by a practice attempt exists.
 */
@Service
public class ContentServiceClient {

    private final RestTemplate restTemplate;
    private final String contentServiceUrl;

    public ContentServiceClient(RestTemplate restTemplate, @Value("${services.content-service-url}") String contentServiceUrl) {
        this.restTemplate = restTemplate;
        this.contentServiceUrl = contentServiceUrl;
    }

    /** P2-A: the parts of content-service's {@code LessonDetailResponse} that practice-service needs. */
    public record LessonRef(UUID id, String title, String audioUrl, boolean isPro, List<SentenceRef> sentences) {
        public Optional<SentenceRef> sentence(UUID sentenceId) {
            return sentences.stream().filter(s -> sentenceId.equals(s.id())).findFirst();
        }
    }

    /** One sentence of a {@link LessonRef}; field names mirror content-service's {@code SentenceResponse}. */
    public record SentenceRef(UUID id, String text, String ipa, String translationVi, Integer sentenceOrder,
                              Integer startMs, Integer endMs, String audioUrl, String imageUrl) {
    }

    /** P2-A: a practised sentence together with its lesson (needed for the Pro gate). */
    public record PracticeTarget(LessonRef lesson, SentenceRef sentence) {
    }

    /** Lesson + sentence of an attempt; 404 when the lesson or the sentence (in that lesson) does not exist. */
    public PracticeTarget fetchSentence(UUID lessonId, UUID sentenceId) {
        LessonRef lesson = fetchLesson(lessonId);
        SentenceRef sentence = lesson.sentence(sentenceId)
                .orElseThrow(() -> ApiException.notFound("Sentence not found in lesson"));
        return new PracticeTarget(lesson, sentence);
    }

    /** 404 → {@link ApiException} not found; unreachable → 502. */
    public LessonRef fetchLesson(UUID lessonId) {
        return findLesson(lessonId).orElseThrow(() -> ApiException.notFound("Lesson not found"));
    }

    /** Empty when content-service answers 404 (deleted lesson); unreachable → 502. */
    public Optional<LessonRef> findLesson(UUID lessonId) {
        Map<?, ?> lesson;
        try {
            lesson = restTemplate.getForObject(contentServiceUrl + "/lessons/{id}", Map.class, lessonId);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Unable to reach content-service to validate lesson");
        }
        return Optional.ofNullable(lesson).map(ContentServiceClient::toLessonRef);
    }

    static LessonRef toLessonRef(Map<?, ?> lesson) {
        List<SentenceRef> sentences = new ArrayList<>();
        if (lesson.get("sentences") instanceof Iterable<?> list) {
            for (Object s : list) {
                if (s instanceof Map<?, ?> m && m.get("id") != null) {
                    sentences.add(new SentenceRef(UUID.fromString(String.valueOf(m.get("id"))),
                            m.get("text") != null ? m.get("text").toString() : "",
                            str(m.get("ipa")), str(m.get("translationVi")), integer(m.get("sentenceOrder")),
                            integer(m.get("startMs")), integer(m.get("endMs")), str(m.get("audioUrl")),
                            str(m.get("imageUrl"))));
                }
            }
        }
        Object id = lesson.get("id");
        return new LessonRef(id != null ? UUID.fromString(id.toString()) : null, str(lesson.get("title")),
                str(lesson.get("audioUrl")),
                // Missing field (content-service older than P2-A) → free lesson.
                Boolean.TRUE.equals(lesson.get("isPro")),
                Collections.unmodifiableList(sentences));
    }

    private static String str(Object o) {
        return o != null ? o.toString() : null;
    }

    private static Integer integer(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }
}
