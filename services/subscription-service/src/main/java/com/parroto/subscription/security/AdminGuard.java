package com.parroto.subscription.security;

import com.parroto.subscription.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Admin check by e-mail allow-list ({@code admin.emails} / env {@code ADMIN_EMAILS}, comma separated). */
@Component
public class AdminGuard {

    private final Set<String> adminEmails;

    public AdminGuard(@Value("${admin.emails:}") String adminEmailsCsv) {
        this.adminEmails = Arrays.stream(adminEmailsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(AuthenticatedUser user) {
        return user != null && user.email() != null && adminEmails.contains(user.email().toLowerCase(Locale.ROOT));
    }

    public void requireAdmin(AuthenticatedUser user) {
        if (!isAdmin(user)) {
            throw ApiException.forbidden("Bạn không có quyền quản trị");
        }
    }
}
