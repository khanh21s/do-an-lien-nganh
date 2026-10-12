package com.parroto.auth.security;

import com.parroto.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and validates the shared-secret JWT. Every other Parroto service carries a
 * read-only copy of {@link #parse(String)} to validate tokens without calling back here.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirySeconds;
    private final long refreshExpirySeconds;

    public JwtService(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.expiry-seconds}") long expirySeconds,
                       @Value("${jwt.refresh-expiry-seconds}") long refreshExpirySeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirySeconds = expirySeconds;
        this.refreshExpirySeconds = refreshExpirySeconds;
    }

    public record IssuedToken(String token, String jti, long expiresIn) {
    }

    public IssuedToken generateAccessToken(User user) {
        return generate(user, expirySeconds, "access");
    }

    public IssuedToken generateRefreshToken(User user) {
        return generate(user, refreshExpirySeconds, "refresh");
    }

    private IssuedToken generate(User user, long ttlSeconds, String type) {
        Instant now = Instant.now();
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("cefrLevel", user.getCefrLevel())
                .claim("type", type)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
        return new IssuedToken(token, jti, ttlSeconds);
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new JwtException("Invalid or expired token", e);
        }
    }

    public long remainingTtlSeconds(Claims claims) {
        long expMillis = claims.getExpiration().getTime();
        long remaining = (expMillis - System.currentTimeMillis()) / 1000;
        return Math.max(remaining, 0);
    }
}
