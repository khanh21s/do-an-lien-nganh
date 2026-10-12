package com.parroto.auth.service;

import com.parroto.auth.dto.AuthResponse;
import com.parroto.auth.entity.User;
import com.parroto.auth.exception.ApiException;
import com.parroto.auth.repository.UserRepository;
import com.parroto.auth.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AuthService {

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
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw ApiException.unauthorized("Account has been deactivated");
        }
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
        // Rotation: the old refresh token is immediately revoked once used.
        blacklistService.blacklist(claims.getId(), jwtService.remainingTtlSeconds(claims));
        return issueTokens(user);
    }

    public void logout(String accessToken) {
        Claims claims;
        try {
            claims = jwtService.parse(accessToken);
        } catch (JwtException e) {
            return; // already invalid/expired, nothing to blacklist
        }
        blacklistService.blacklist(claims.getId(), jwtService.remainingTtlSeconds(claims));
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
