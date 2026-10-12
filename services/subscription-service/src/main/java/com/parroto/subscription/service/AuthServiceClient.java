package com.parroto.subscription.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Resolves a user's email by calling auth-service's internal endpoint, so
 * subscription-service can send payment confirmation emails without owning user data.
 */
@Service
public class AuthServiceClient {

    private final RestTemplate restTemplate;
    private final String authServiceUrl;

    public AuthServiceClient(RestTemplate restTemplate, @Value("${services.auth-service-url}") String authServiceUrl) {
        this.restTemplate = restTemplate;
        this.authServiceUrl = authServiceUrl;
    }

    public String findEmail(UUID userId) {
        try {
            Map<?, ?> response = restTemplate.getForObject(authServiceUrl + "/internal/users/{id}", Map.class, userId);
            return response != null ? (String) response.get("email") : null;
        } catch (RestClientException e) {
            return null;
        }
    }
}
