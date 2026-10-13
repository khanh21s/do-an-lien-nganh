package com.parroto.auth.service;

import java.util.Locale;

/** Decides whether a social-login client id (GOOGLE_CLIENT_ID / APPLE_CLIENT_ID) is really configured. */
final class OAuthClientIds {

    private OAuthClientIds() {
    }

    /**
     * Blank values and the obvious placeholders shipped in {@code .env.example}
     * ({@code your_...}, {@code your-...}, {@code changeme}) count as "not configured" (AUTH-04 → 503).
     */
    static boolean isConfigured(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return false;
        }
        String value = clientId.trim().toLowerCase(Locale.ROOT);
        return !(value.startsWith("your_") || value.startsWith("your-") || value.equals("changeme"));
    }
}
