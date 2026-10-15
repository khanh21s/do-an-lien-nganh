package com.parroto.common.security;

import com.parroto.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminGuardTest {

    private final AdminGuard guard = new AdminGuard(" Admin@Parroto.com , second@x.io,, ");

    @Test
    void parsesCsvCaseInsensitively() {
        assertThat(guard.adminEmails()).containsExactlyInAnyOrder("admin@parroto.com", "second@x.io");
        assertThat(guard.isAdminEmail("ADMIN@parroto.com")).isTrue();
        assertThat(guard.isAdminEmail("user@parroto.com")).isFalse();
        assertThat(guard.isAdminEmail(null)).isFalse();
    }

    @Test
    void adminPasses() {
        assertThatCode(() -> guard.requireAdmin(new AuthenticatedUser("1", "second@X.io", "B1")))
                .doesNotThrowAnyException();
    }

    @Test
    void nonAdminOrMissingPrincipalIs403WithVietnameseMessage() {
        for (AuthenticatedUser user : new AuthenticatedUser[]{
                new AuthenticatedUser("1", "user@parroto.com", "B1"),
                new AuthenticatedUser("1", null, "B1"),
                null}) {
            assertThatThrownBy(() -> guard.requireAdmin(user)).isInstanceOfSatisfying(ApiException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(e.getMessage()).isEqualTo("Bạn không có quyền quản trị");
            });
        }
    }

    @Test
    void emptyConfigMeansNobodyIsAdmin() {
        AdminGuard empty = new AdminGuard("");
        assertThat(empty.adminEmails()).isEmpty();
        assertThat(empty.isAdmin(new AuthenticatedUser("1", "admin@parroto.com", "B1"))).isFalse();
    }

    @Test
    void forbiddenMessageCanBeOverriddenPerService() {
        // content-service keeps its historical English message via admin.forbidden-message.
        AdminGuard content = new AdminGuard("admin@parroto.com", "Admin privileges required");
        assertThatThrownBy(() -> content.requireAdmin(new AuthenticatedUser("1", "user@parroto.com", "B1")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getMessage()).isEqualTo("Admin privileges required");
                });
        assertThatCode(() -> content.requireAdmin(new AuthenticatedUser("1", "ADMIN@parroto.com", "B1")))
                .doesNotThrowAnyException();
    }
}
