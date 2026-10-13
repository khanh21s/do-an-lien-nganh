package com.parroto.auth.config;

import com.parroto.auth.entity.User;
import com.parroto.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminBootstrapTest {

    private static final String PW = "Str0ng-admin-pass";
    private final UserRepository repo = mock(UserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private AdminBootstrap bootstrap(String email, String password, boolean reset) {
        return new AdminBootstrap(repo, encoder, email, password, reset, "admin@parroto.com");
    }

    @Test
    void createsTheAdminWhenMissing() {
        when(repo.findByEmail("admin@parroto.com")).thenReturn(Optional.empty());

        bootstrap("  Admin@Parroto.com ", PW, false).run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@parroto.com");
        assertThat(saved.getValue().getAuthProvider()).isEqualTo("email");
        assertThat(encoder.matches(PW, saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void leavesAnExistingAdminUntouchedByDefault() {
        User existing = User.builder().email("admin@parroto.com").passwordHash("old").build();
        when(repo.findByEmail("admin@parroto.com")).thenReturn(Optional.of(existing));

        bootstrap("admin@parroto.com", PW, false).run(null);

        verify(repo, never()).save(any());
        assertThat(existing.getPasswordHash()).isEqualTo("old");
    }

    @Test
    void resetsThePasswordWhenAsked() {
        User existing = User.builder().email("admin@parroto.com").passwordHash("old").build();
        when(repo.findByEmail("admin@parroto.com")).thenReturn(Optional.of(existing));

        bootstrap("admin@parroto.com", PW, true).run(null);

        verify(repo).save(existing);
        assertThat(encoder.matches(PW, existing.getPasswordHash())).isTrue();
    }

    @Test
    void skipsWhenNotConfiguredOrPasswordTooShort() {
        bootstrap("", "", false).run(null);
        bootstrap("admin@parroto.com", "short", false).run(null);
        verifyNoInteractions(repo);
    }
}
