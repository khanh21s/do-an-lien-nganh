package com.parroto.content.security;

import java.util.UUID;

public record AuthenticatedUser(String userId, String email, String cefrLevel) {
    public UUID userUuid() {
        return UUID.fromString(userId);
    }
}
