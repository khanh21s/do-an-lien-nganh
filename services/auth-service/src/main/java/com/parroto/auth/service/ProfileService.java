package com.parroto.auth.service;

import com.parroto.auth.dto.ProfileResponse;
import com.parroto.auth.dto.UpdateProfileRequest;
import com.parroto.auth.entity.User;
import com.parroto.auth.exception.ApiException;
import com.parroto.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Set<String> adminEmails;

    public ProfileService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           @Value("${admin.emails:}") String adminEmailsCsv) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmails = Arrays.stream(adminEmailsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID userId) {
        User user = findActiveUser(userId);
        return ProfileResponse.from(user, isAdmin(user));
    }

    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = findActiveUser(userId);
        if (request.cefrLevel() != null && !request.cefrLevel().isBlank()) {
            user.setCefrLevel(request.cefrLevel());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl());
        }
        // saveAndFlush: the response reads @UpdateTimestamp, only refreshed in-memory once the
        // UPDATE actually executes (a plain save() only guarantees that at transaction commit).
        User saved = userRepository.saveAndFlush(user);
        return ProfileResponse.from(saved, isAdmin(saved));
    }

    // MVP stand-in for a real roles table, matching the ADMIN_EMAILS allow-list every
    // other service already enforces server-side for admin writes (see each service's
    // AdminGuard) — this only drives whether the frontend SHOWS admin navigation; the
    // actual write endpoints are still gated independently by their own service.
    private boolean isAdmin(User user) {
        return user.getEmail() != null && adminEmails.contains(user.getEmail().toLowerCase());
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = findActiveUser(userId);
        if (user.getPasswordHash() == null) {
            throw ApiException.badRequest("This account signs in via a social provider and has no password");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.unauthorized("Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("New password must be different from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        User user = findActiveUser(userId);
        user.setIsActive(false);
        userRepository.save(user);
    }

    private User findActiveUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw ApiException.notFound("User not found");
        }
        return user;
    }
}
