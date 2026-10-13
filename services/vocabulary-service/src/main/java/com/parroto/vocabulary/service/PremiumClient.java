package com.parroto.vocabulary.service;

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

/**
 * Checks whether a user has an active subscription, by calling subscription-service's
 * internal endpoint (authenticated with the shared {@code X-Internal-Token}, architecture §5.3).
 * Fails closed (treats the user as non-premium) if subscription-service is unreachable,
 * rather than blocking the whole request.
 *
 * Result is cached in Redis under {@code premium:{userId}} for 5 minutes; subscription-service
 * deletes that key whenever it activates premium (SUB-04) so upgrades take effect immediately.
 */
@Service
public class PremiumClient {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
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

    public boolean isPremium(String userId) {
        String cacheKey = cacheKey(userId);
        Boolean cached = cacheService.get(cacheKey, Boolean.class);
        if (cached != null) {
            return cached;
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
            // Don't cache a failure: retry on the next request instead of locking the user out for 5 minutes.
            return false;
        }
        cacheService.set(cacheKey, premium, Duration.ofMinutes(5));
        return premium;
    }
}
