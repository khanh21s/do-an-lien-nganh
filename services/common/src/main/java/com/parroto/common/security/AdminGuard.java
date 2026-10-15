package com.parroto.common.security;

import com.parroto.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin check by e-mail allow-list ({@code admin.emails} / env {@code ADMIN_EMAILS}, comma separated),
 * shared by every service that has admin endpoints. The 403 message defaults to
 * {@value #FORBIDDEN_MESSAGE}; a service can keep its historical wording with
 * {@code admin.forbidden-message} (content-service: "Admin privileges required").
 */
@Component
public class AdminGuard {

    public static final String FORBIDDEN_MESSAGE = "Bạn không có quyền quản trị";

    private final Set<String> adminEmails;
    private final String forbiddenMessage;

    public AdminGuard(String adminEmailsCsv) {
        this(adminEmailsCsv, FORBIDDEN_MESSAGE);
    }

    @Autowired
    public AdminGuard(@Value("${admin.emails:}") String adminEmailsCsv,
                      @Value("${admin.forbidden-message:" + FORBIDDEN_MESSAGE + "}") String forbiddenMessage) {
        this.forbiddenMessage = forbiddenMessage;
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
            throw ApiException.forbidden(forbiddenMessage);
        }
    }
}
