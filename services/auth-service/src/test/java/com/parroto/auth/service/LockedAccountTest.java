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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T14-AUTH: a locked account gets 403 "Tài khoản đã bị khoá" on login and on refresh-token. */
class LockedAccountTest {

    private static final String SECRET = "unit_test_secret_that_is_at_least_32_bytes_long";

    private final JwtService jwtService = new JwtService(SECRET, 3600, 604800);
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private TokenBlacklistService blacklistService;
    private GoogleTokenVerifier googleTokenVerifier;
    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        blacklistService = mock(TokenBlacklistService.class);
        googleTokenVerifier = mock(GoogleTokenVerifier.class);
        user = User.builder().id(UUID.randomUUID()).email("a@example.com").cefrLevel("B1")
                .passwordHash("hash").isActive(true).build();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        authService = new AuthService(userRepository, passwordEncoder, jwtService, blacklistService,
                googleTokenVerifier, mock(AppleTokenVerifier.class));
    }

    private static void assertLocked(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(e.getMessage()).isEqualTo("Tài khoản đã bị khoá");
        });
    }

    @Test
    void refreshWorksWhileActiveAndIsBlockedOnceLocked() {
        String refresh = jwtService.generateRefreshToken(user).token();
        String refresh2 = jwtService.generateRefreshToken(user).token();
        AuthResponse ok = authService.refresh(refresh);
        assertThat(ok.token()).isNotBlank();

        user.setIsActive(false);

        assertLocked(() -> authService.refresh(refresh2));
        // Not rotated: the locked user's refresh token is not consumed (it stays useless while locked).
        verify(blacklistService, never()).blacklist(jwtService.parse(refresh2).getId(), 604800L);
    }

    @Test
    void loginOfLockedAccountIs403AfterPasswordCheck() {
        user.setIsActive(false);
        assertLocked(() -> authService.login("a@example.com", "secret"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void wrongPasswordOnLockedAccountStaysGeneric401() {
        user.setIsActive(false);
        assertThatThrownBy(() -> authService.login("a@example.com", "wrong"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void googleLoginOfLockedAccountIs403() {
        user.setIsActive(false);
        when(googleTokenVerifier.verify("g-token"))
                .thenReturn(new GoogleTokenVerifier.VerifiedGoogleUser("a@example.com", true, "sub"));
        assertLocked(() -> authService.loginWithGoogle("g-token"));
    }

    @Test
    void loginOfActiveAccountStillWorks() {
        AuthResponse response = authService.login("a@example.com", "secret");
        assertThat(response.userId()).isEqualTo(user.getId());
        verify(blacklistService, never()).blacklist(anyString(), anyLong());
    }
}
