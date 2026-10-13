package com.parroto.vocabulary.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Validate-only counterpart of auth-service's JwtService: every service shares the same
 * signing secret and verifies tokens independently without calling back to auth-service.
 */
@Service
public class JwtValidator {

    /** HS256 needs a key of at least 256 bits. */
    public static final int MIN_SECRET_BYTES = 32;
    public static final String ACCESS_TOKEN_TYPE = "access";

    private final SecretKey key;

    public JwtValidator(@Value("${jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank()
                || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least " + MIN_SECRET_BYTES + " bytes long");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Verifies signature and expiry only (any token type). */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new JwtException("Invalid or expired token", e);
        }
    }

    /** Verifies the token and requires {@code type=access}: refresh tokens are never valid as Bearer. */
    public Claims parseAccessToken(String token) {
        Claims claims = parse(token);
        if (!ACCESS_TOKEN_TYPE.equals(claims.get("type", String.class))) {
            throw new JwtException("Token is not an access token");
        }
        return claims;
    }
}
