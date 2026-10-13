package com.parroto.auth.dto;

/** Optional body of {@code POST /auth/logout}: the refresh token to revoke together with the access token. */
public record LogoutRequest(String refreshToken) {
}
