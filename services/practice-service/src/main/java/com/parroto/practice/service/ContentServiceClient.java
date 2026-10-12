package com.parroto.practice.service;

import com.parroto.practice.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
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

    /**
     * @return the sentence's reference English text
     */
    @SuppressWarnings("unchecked")
    public String fetchSentenceText(UUID lessonId, UUID sentenceId) {
        Map<String, Object> lesson;
        try {
            lesson = restTemplate.getForObject(contentServiceUrl + "/lessons/{id}", Map.class, lessonId);
        } catch (HttpClientErrorException.NotFound e) {
            throw ApiException.notFound("Lesson not found");
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Unable to reach content-service to validate lesson");
        }
        if (lesson == null) {
            throw ApiException.notFound("Lesson not found");
        }
        Object sentences = lesson.get("sentences");
        if (sentences instanceof Iterable<?> list) {
            for (Object s : list) {
                if (s instanceof Map<?, ?> sentenceMap && sentenceId.toString().equals(String.valueOf(sentenceMap.get("id")))) {
                    Object text = sentenceMap.get("text");
                    return text != null ? text.toString() : "";
                }
            }
        }
        throw ApiException.notFound("Sentence not found in lesson");
    }

    public void assertLessonExists(UUID lessonId) {
        try {
            restTemplate.getForObject(contentServiceUrl + "/lessons/{id}", Map.class, lessonId);
        } catch (HttpClientErrorException.NotFound e) {
            throw ApiException.notFound("Lesson not found");
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Unable to reach content-service to validate lesson");
        }
    }
}
