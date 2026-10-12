package com.parroto.vocabulary.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * Checks whether a user has an active subscription, by calling subscription-service's
 * internal endpoint. Fails closed (treats the user as non-premium) if subscription-service
 * is unreachable, rather than blocking the whole request.
 */
@Service
public class PremiumClient {

    private final RestTemplate restTemplate;
    private final String subscriptionServiceUrl;
    private final CacheService cacheService;

    public PremiumClient(RestTemplate restTemplate, @Value("${services.subscription-service-url}") String subscriptionServiceUrl,
                          CacheService cacheService) {
        this.restTemplate = restTemplate;
        this.subscriptionServiceUrl = subscriptionServiceUrl;
        this.cacheService = cacheService;
    }

    public boolean isPremium(String userId) {
        String cacheKey = "premium:" + userId;
        Boolean cached = cacheService.get(cacheKey, Boolean.class);
        if (cached != null) {
            return cached;
        }
        boolean premium;
        try {
            Map<?, ?> response = restTemplate.getForObject(
                    subscriptionServiceUrl + "/internal/subscriptions/{userId}/active", Map.class, userId);
            premium = response != null && Boolean.TRUE.equals(response.get("isPremium"));
        } catch (RestClientException e) {
            premium = false;
        }
        cacheService.set(cacheKey, premium, Duration.ofMinutes(5));
        return premium;
    }
}
