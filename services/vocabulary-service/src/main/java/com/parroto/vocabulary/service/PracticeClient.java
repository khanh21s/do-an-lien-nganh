package com.parroto.vocabulary.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * T16: tells practice-service that the user met the game's daily goal on a day, so the day
 * counts for the practice streak ({@code POST /internal/goal-days}, X-Internal-Token).
 * Never throws: returns false when practice-service is unreachable (retried on the next round).
 */
@Service
public class PracticeClient {

    private static final Logger log = LoggerFactory.getLogger(PracticeClient.class);

    private final RestTemplate restTemplate;
    private final String practiceServiceUrl;
    private final String internalToken;

    public PracticeClient(RestTemplate restTemplate,
                          @Value("${services.practice-service-url}") String practiceServiceUrl,
                          @Value("${internal.token}") String internalToken) {
        this.restTemplate = restTemplate;
        this.practiceServiceUrl = practiceServiceUrl;
        this.internalToken = internalToken;
    }

    public boolean recordGoalDay(UUID userId, LocalDate day) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(PremiumClient.INTERNAL_TOKEN_HEADER, internalToken);
        Map<String, Object> body = Map.of("userId", userId.toString(), "day", day.toString(), "source", "game");
        try {
            restTemplate.postForEntity(practiceServiceUrl + "/internal/goal-days", new HttpEntity<>(body, headers), Map.class);
            return true;
        } catch (RestClientException e) {
            log.warn("Could not record goal day {} for user {}: {}", day, userId, e.getMessage());
            return false;
        }
    }
}
