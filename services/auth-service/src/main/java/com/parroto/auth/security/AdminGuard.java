package com.parroto.auth.security;

import com.parroto.auth.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin check by e-mail allow-list ({@code admin.emails} / env {@code ADMIN_EMAILS}, comma separated),
 * the same server-side rule every other service's AdminGuard enforces.
 */
@Component
public class AdminGuard {

    public static final String FORBIDDEN_MESSAGE = "Bạn không có quyền quản trị";

    private final Set<String> adminEmails;

    public AdminGuard(@Value("${admin.emails:}") String adminEmailsCsv) {
        this.adminEmails = Arrays.stream(adminEmailsCsv == null ? new String[0] : adminEmailsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Lower-cased admin e-mails. */
    public Set<String> adminEmails() {
        return adminEmails;
    }

    public boolean isAdminEmail(String email) {
        return email != null && adminEmails.contains(email.trim().toLowerCase(Locale.ROOT));
    }

    public boolean isAdmin(AuthenticatedUser user) {
        return user != null && isAdminEmail(user.email());
    }

    public void requireAdmin(AuthenticatedUser user) {
        if (!isAdmin(user)) {
            throw ApiException.forbidden(FORBIDDEN_MESSAGE);
        }
    }
}
