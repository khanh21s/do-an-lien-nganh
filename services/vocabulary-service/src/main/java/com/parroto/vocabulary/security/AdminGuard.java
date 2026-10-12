package com.parroto.vocabulary.security;

import com.parroto.vocabulary.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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
