package com.parroto.subscription.service;

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

import java.util.Map;
import java.util.UUID;

/**
 * Resolves a user's email by calling auth-service's internal endpoint (authenticated with the
 * shared {@code X-Internal-Token}, architecture §5.3), so subscription-service can send payment
 * confirmation emails and show who redeemed an activation code without owning user data.
 * Any failure resolves to {@code null}.
 */
@Service
public class AuthServiceClient {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final Logger log = LoggerFactory.getLogger(AuthServiceClient.class);

    private final RestTemplate restTemplate;
    private final String authServiceUrl;
    private final String internalToken;

    public AuthServiceClient(RestTemplate restTemplate,
                              @Value("${services.auth-service-url}") String authServiceUrl,
                              @Value("${internal.token}") String internalToken) {
        this.restTemplate = restTemplate;
        this.authServiceUrl = authServiceUrl;
        this.internalToken = internalToken;
    }

    public String findEmail(UUID userId) {
        if (userId == null) {
            return null;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(INTERNAL_TOKEN_HEADER, internalToken);
            ResponseEntity<Map> response = restTemplate.exchange(authServiceUrl + "/internal/users/{id}",
                    HttpMethod.GET, new HttpEntity<>(headers), Map.class, userId);
            Map<?, ?> body = response.getBody();
            Object email = body != null ? body.get("email") : null;
            return email instanceof String s ? s : null;
        } catch (RestClientException e) {
            log.warn("Could not resolve email for user {}: {}", userId, e.getMessage());
            return null;
        }
    }
}
