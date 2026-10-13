package com.parroto.auth.service;

import com.parroto.auth.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Verifies a Google ID token by calling Google's tokeninfo endpoint (simpler and more
 * container-friendly than pulling in the full google-api-client dependency tree).
 * https://developers.google.com/identity/sign-in/web/backend-auth
 */
@Service
public class GoogleTokenVerifier {

    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";

    private final RestTemplate restTemplate;
    private final String expectedClientId;

    public GoogleTokenVerifier(RestTemplate restTemplate, @Value("${oauth.google.client-id}") String expectedClientId) {
        this.restTemplate = restTemplate;
        this.expectedClientId = expectedClientId;
    }

    public record VerifiedGoogleUser(String email, boolean emailVerified, String subject) {
    }

    @SuppressWarnings("unchecked")
    public VerifiedGoogleUser verify(String idToken) {
        // AUTH-04: never accept a token without checking its audience; no client id = feature off.
        if (expectedClientId == null || expectedClientId.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Google login chưa cấu hình");
        }
        Map<String, Object> payload;
        try {
            payload = restTemplate.getForObject(TOKENINFO_URL + idToken, Map.class);
        } catch (RestClientException e) {
            throw ApiException.unauthorized("Invalid Google token");
        }
        if (payload == null || !payload.containsKey("email")) {
            throw ApiException.unauthorized("Invalid Google token");
        }
        Object aud = payload.get("aud");
        if (aud == null || !expectedClientId.equals(aud.toString())) {
            throw ApiException.unauthorized("Google token was not issued for this application");
        }
        return new VerifiedGoogleUser(
                payload.get("email").toString(),
                Boolean.parseBoolean(String.valueOf(payload.get("email_verified"))),
                String.valueOf(payload.get("sub")));
    }
}
