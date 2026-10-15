package com.parroto.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private static final String SECRET = "unit_test_secret_value_at_least_32_bytes_long";

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final JwtAuthFilter filter = new JwtAuthFilter(new JwtValidator(SECRET), redis);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static String token(String type, String jti) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject("2b7e3c1a-0000-4000-8000-000000000001")
                .claim("email", "user@parroto.com")
                .claim("cefrLevel", "B1")
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)));
        if (jti != null) {
            builder.id(jti);
        }
        return builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    }

    private Authentication run(String bearer) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        if (bearer != null) {
            request.addHeader("Authorization", "Bearer " + bearer);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).as("filter always continues the chain").isNotNull();
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void validAccessTokenAuthenticatesWithClaims() throws Exception {
        when(redis.hasKey("token_blacklist:j1")).thenReturn(false);
        Authentication auth = run(token("access", "j1"));
        assertThat(auth).isNotNull();
        AuthenticatedUser user = (AuthenticatedUser) auth.getPrincipal();
        assertThat(user.userId()).isEqualTo("2b7e3c1a-0000-4000-8000-000000000001");
        assertThat(user.email()).isEqualTo("user@parroto.com");
        assertThat(user.cefrLevel()).isEqualTo("B1");
    }

    @Test
    void blacklistedRefreshMissingJtiOrGarbageStayAnonymous() throws Exception {
        when(redis.hasKey("token_blacklist:revoked")).thenReturn(true);
        assertThat(run(token("access", "revoked"))).isNull();
        assertThat(run(token("refresh", "j2"))).isNull();
        assertThat(run(token("access", null))).isNull();
        assertThat(run("not-a-jwt")).isNull();
        assertThat(run(null)).isNull();
    }

    @Test
    void redisDownFailsOpen() throws Exception {
        when(redis.hasKey("token_blacklist:j3")).thenThrow(new RedisConnectionFailureException("down"));
        assertThat(run(token("access", "j3"))).isNotNull();
    }
}
