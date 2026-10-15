package com.parroto.practice.service;

import com.parroto.common.cache.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * P2-A1: premium status from subscription-service {@code GET /internal/subscriptions/{userId}/active}
 * (header {@code X-Internal-Token}, architecture §5.3). Same pattern and the same shared Redis key
 * {@code premium:{userId}} (5 min, JSON boolean) as vocabulary-service's PremiumClient, so
 * subscription-service's eviction on purchase/activation (SUB-04) also refreshes this service.
 */
@Service
public class PremiumClient {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    static final Duration TTL = Duration.ofMinutes(5);
    private static final Logger log = LoggerFactory.getLogger(PremiumClient.class);

    private final RestTemplate restTemplate;
    private final String subscriptionServiceUrl;
    private final CacheService cacheService;
    private final String internalToken;

    public PremiumClient(RestTemplate restTemplate,
                         @Value("${services.subscription-service-url}") String subscriptionServiceUrl,
                         CacheService cacheService,
                         @Value("${internal.token}") String internalToken) {
        this.restTemplate = restTemplate;
        this.subscriptionServiceUrl = subscriptionServiceUrl;
        this.cacheService = cacheService;
        this.internalToken = internalToken;
    }

    public static String cacheKey(String userId) {
        return "premium:" + userId;
    }

    /** Empty when subscription-service could not be asked (failures are not cached). */
    public Optional<Boolean> lookup(String userId) {
        String key = cacheKey(userId);
        Boolean cached = cacheService.get(key, Boolean.class);
        if (cached != null) {
            return Optional.of(cached);
        }
        boolean premium;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(INTERNAL_TOKEN_HEADER, internalToken);
            ResponseEntity<Map> response = restTemplate.exchange(
                    subscriptionServiceUrl + "/internal/subscriptions/{userId}/active",
                    HttpMethod.GET, new HttpEntity<>(headers), Map.class, userId);
            Map<?, ?> body = response.getBody();
            premium = body != null && Boolean.TRUE.equals(body.get("isPremium"));
        } catch (RestClientException e) {
            log.warn("Premium check failed for user {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
        cacheService.set(key, premium, TTL);
        return Optional.of(premium);
    }
}
