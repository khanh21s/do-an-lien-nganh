package com.parroto.auth.security;

import java.util.UUID;

/**
 * Principal populated from a validated JWT's claims. {@code userId} is the JWT {@code sub} claim.
 */
public record AuthenticatedUser(String userId, String email, String cefrLevel) {
    public UUID userUuid() {
        return UUID.fromString(userId);
    }
}
