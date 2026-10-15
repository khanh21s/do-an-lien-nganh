package com.parroto.auth.service;

import com.parroto.auth.dto.AuthResponse;
import com.parroto.auth.entity.User;
import com.parroto.common.exception.ApiException;
import com.parroto.auth.repository.UserRepository;
import com.parroto.auth.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class AuthService {

    public static final String ACCOUNT_LOCKED = "Tài khoản đã bị khoá";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenBlacklistService blacklistService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final AppleTokenVerifier appleTokenVerifier;

    public AuthService(UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        TokenBlacklistService blacklistService,
                        GoogleTokenVerifier googleTokenVerifier,
                        AppleTokenVerifier appleTokenVerifier) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.blacklistService = blacklistService;
        this.googleTokenVerifier = googleTokenVerifier;
        this.appleTokenVerifier = appleTokenVerifier;
    }

    @Transactional
    public AuthResponse register(String email, String rawPassword, String cefrLevel) {
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Email already registered");
        }
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .authProvider("email")
                .cefrLevel(cefrLevel != null ? cefrLevel : "B1")
                .build();
        user = userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        ensureNotLocked(user);
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse loginWithGoogle(String googleToken) {
        GoogleTokenVerifier.VerifiedGoogleUser verified = googleTokenVerifier.verify(googleToken);
        User user = userRepository.findByEmail(verified.email())
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(verified.email())
                        .authProvider("google")
                        .cefrLevel("B1")
                        .build()));
        ensureNotLocked(user);
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse loginWithApple(String appleToken) {
        AppleTokenVerifier.VerifiedAppleUser verified = appleTokenVerifier.verify(appleToken);
        if (verified.email() == null) {
            throw ApiException.badRequest("Apple did not share an email for this account");
        }
        User user = userRepository.findByEmail(verified.email())
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(verified.email())
                        .authProvider("apple")
                        .cefrLevel("B1")
                        .build()));
        ensureNotLocked(user);
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtService.parse(refreshToken);
        } catch (JwtException e) {
            throw ApiException.unauthorized("Invalid or expired refresh token");
        }
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw ApiException.unauthorized("Token is not a refresh token");
        }
        if (blacklistService.isBlacklisted(claims.getId())) {
            throw ApiException.unauthorized("Refresh token has been revoked");
        }
        User user = userRepository.findById(UUID.fromString(claims.getSubject()))
                .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
        // T14: a locked account cannot obtain new tokens (its current access token simply expires, <= 1h).
        ensureNotLocked(user);
        // Rotation: the old refresh token is immediately revoked once used.
        blacklistService.blacklist(claims.getId(), jwtService.remainingTtlSeconds(claims));
        return issueTokens(user);
    }

    /** Backward-compatible overload: revokes only the access token. */
    public void logout(String accessToken) {
        logout(accessToken, null);
    }

    /**
     * Revokes the access token and, when given, the refresh token: each jti is blacklisted in Redis for
     * the token's remaining lifetime, so {@link #refresh(String)} rejects the refresh token with 401.
     * Invalid/expired tokens are skipped (nothing left to revoke), as is a refresh token that belongs to
     * another user than the access token.
     */
    public void logout(String accessToken, String refreshToken) {
        Claims access = parseQuietly(accessToken);
        if (access != null) {
            blacklistService.blacklist(access.getId(), jwtService.remainingTtlSeconds(access));
        }
        Claims refresh = parseQuietly(refreshToken);
        if (refresh == null || !"refresh".equals(refresh.get("type", String.class))) {
            return;
        }
        if (access != null && !Objects.equals(access.getSubject(), refresh.getSubject())) {
            return;
        }
        blacklistService.blacklist(refresh.getId(), jwtService.remainingTtlSeconds(refresh));
    }

    private Claims parseQuietly(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return jwtService.parse(token);
        } catch (JwtException e) {
            return null;
        }
    }

    /**
     * T14: locked (is_active = false, set by an admin or by self-deactivation) accounts get 403 on login
     * (password, Google, Apple) and on refresh-token. Checked after credentials, so the lock state is not
     * revealed to someone who does not know the password.
     */
    static void ensureNotLocked(User user) {
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw ApiException.forbidden(ACCOUNT_LOCKED);
        }
    }

    private AuthResponse issueTokens(User user) {
        JwtService.IssuedToken access = jwtService.generateAccessToken(user);
        JwtService.IssuedToken refresh = jwtService.generateRefreshToken(user);
        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .cefrLevel(user.getCefrLevel())
                .token(access.token())
                .refreshToken(refresh.token())
                .expiresIn(access.expiresIn())
                .build();
    }
}
