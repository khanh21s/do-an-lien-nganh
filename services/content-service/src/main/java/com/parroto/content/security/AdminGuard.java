package com.parroto.content.security;

import com.parroto.content.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MVP stand-in for a real roles/permissions table: an allow-list of admin emails
 * supplied via the ADMIN_EMAILS environment variable.
 */
@Component
public class AdminGuard {

    private final Set<String> adminEmails;

    public AdminGuard(@Value("${admin.emails:}") String adminEmailsCsv) {
        this.adminEmails = Arrays.stream(adminEmailsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    public void requireAdmin(AuthenticatedUser user) {
        if (user == null || user.email() == null || !adminEmails.contains(user.email().toLowerCase())) {
            throw ApiException.forbidden("Admin privileges required");
        }
    }
}
