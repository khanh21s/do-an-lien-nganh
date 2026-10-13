package com.parroto.auth.service;

import com.parroto.auth.dto.AuthResponse;
import com.parroto.auth.entity.User;
import com.parroto.auth.exception.ApiException;
import com.parroto.auth.repository.UserRepository;
import com.parroto.auth.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Review wave 1 AUTH-03-REFRESH: logout must revoke the refresh token as well. */
class LogoutRevokesRefreshTokenTest {

    private static final String SECRET = "unit_test_secret_that_is_at_least_32_bytes_long";

    private final JwtService jwtService = new JwtService(SECRET, 3600, 604800);
    /** jti -> ttl seconds, standing in for Redis. */
    private final Map<String, Long> blacklist = new HashMap<>();
    private UserRepository userRepository;
    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        TokenBlacklistService blacklistService = mock(TokenBlacklistService.class);
        doAnswer(inv -> {
            long ttl = inv.getArgument(1);
            if (ttl > 0) {
                blacklist.put(inv.getArgument(0), ttl);
            }
            return null;
        }).when(blacklistService).blacklist(anyString(), anyLong());
        when(blacklistService.isBlacklisted(anyString()))
                .thenAnswer(inv -> blacklist.containsKey(inv.<String>getArgument(0)));

        userRepository = mock(UserRepository.class);
        user = User.builder().id(UUID.randomUUID()).email("a@example.com").cefrLevel("B1").isActive(true).build();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        authService = new AuthService(userRepository, mock(PasswordEncoder.class), jwtService, blacklistService,
                mock(GoogleTokenVerifier.class), mock(AppleTokenVerifier.class));
    }

    private static void assertUnauthorized(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void logoutWithRefreshTokenBlacklistsBothAndRefreshIsRejected() {
        String access = jwtService.generateAccessToken(user).token();
        String refresh = jwtService.generateRefreshToken(user).token();

        authService.logout(access, refresh);

        String refreshJti = jwtService.parse(refresh).getId();
        assertThat(blacklist).containsKeys(jwtService.parse(access).getId(), refreshJti);
        // TTL = remaining lifetime of the refresh token (7 days here), not the access token's.
        assertThat(blacklist.get(refreshJti)).isBetween(604800L - 60, 604800L);
        assertUnauthorized(() -> authService.refresh(refresh));
    }

    @Test
    void logoutWithoutBodyKeepsOldBehaviour() {
        String access = jwtService.generateAccessToken(user).token();
        String refresh = jwtService.generateRefreshToken(user).token();

        authService.logout(access);

        assertThat(blacklist).containsOnlyKeys(jwtService.parse(access).getId());
        AuthResponse rotated = authService.refresh(refresh);
        assertThat(rotated.refreshToken()).isNotBlank();
    }

    @Test
    void refreshTokenRevokedEvenWhenAccessTokenIsInvalid() {
        String refresh = jwtService.generateRefreshToken(user).token();

        authService.logout("not-a-jwt", refresh);

        assertThat(blacklist).containsOnlyKeys(jwtService.parse(refresh).getId());
        assertUnauthorized(() -> authService.refresh(refresh));
    }

    @Test
    void accessTokenInRefreshFieldIsNotTreatedAsRefreshToken() {
        String access = jwtService.generateAccessToken(user).token();
        String otherAccess = jwtService.generateAccessToken(user).token();

        authService.logout(access, otherAccess);

        assertThat(blacklist).containsOnlyKeys(jwtService.parse(access).getId());
    }

    @Test
    void refreshTokenOfAnotherUserIsIgnored() {
        User other = User.builder().id(UUID.randomUUID()).email("b@example.com").cefrLevel("B1").build();
        String access = jwtService.generateAccessToken(user).token();
        String othersRefresh = jwtService.generateRefreshToken(other).token();

        authService.logout(access, othersRefresh);

        assertThat(blacklist).doesNotContainKey(jwtService.parse(othersRefresh).getId());
    }

    @Test
    void garbageRefreshTokenIsIgnored() {
        String access = jwtService.generateAccessToken(user).token();

        authService.logout(access, "garbage");
        authService.logout(access, "  ");

        assertThat(blacklist).containsOnlyKeys(jwtService.parse(access).getId());
    }
}
