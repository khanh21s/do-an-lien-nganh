package com.parroto.auth.security;

import com.parroto.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit_test_secret_that_is_at_least_32_bytes_long";

    private final JwtService jwtService = new JwtService(SECRET, 3600, 604800);

    private static User user() {
        return User.builder().id(UUID.randomUUID()).email("a@example.com").cefrLevel("B1").build();
    }

    @Test
    void acceptsAccessTokenAsBearer() {
        User user = user();
        String token = jwtService.generateAccessToken(user).token();

        Claims claims = jwtService.parseAccessToken(token);

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("type", String.class)).isEqualTo("access");
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    void rejectsRefreshTokenAsBearer() {
        String refresh = jwtService.generateRefreshToken(user()).token();

        assertThatThrownBy(() -> jwtService.parseAccessToken(refresh)).isInstanceOf(JwtException.class);
        // Still a valid token for the /auth/refresh-token flow.
        assertThat(jwtService.parse(refresh).get("type", String.class)).isEqualTo("refresh");
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        JwtService other = new JwtService("another_secret_that_is_also_at_least_32_bytes", 3600, 604800);
        String token = other.generateAccessToken(user()).token();

        assertThatThrownBy(() -> jwtService.parseAccessToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void alwaysSignsWithHs256EvenForLongSecrets() {
        // A >= 64-byte secret would make jjwt default to HS512, which the gateway rejects.
        JwtService longKey = new JwtService("x".repeat(68), 3600, 604800);
        String token = longKey.generateAccessToken(user()).token();
        String header = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[0]),
                java.nio.charset.StandardCharsets.UTF_8);

        assertThat(header).contains("\"alg\":\"HS256\"");
    }

    @Test
    void failsFastOnShortSecret() {
        assertThatThrownBy(() -> new JwtService("too_short_secret", 3600, 604800))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void failsFastOnMissingSecret() {
        assertThatThrownBy(() -> new JwtService("", 3600, 604800)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService(null, 3600, 604800)).isInstanceOf(IllegalStateException.class);
    }
}
