package com.parroto.auth.service;

import com.parroto.auth.entity.User;
import com.parroto.auth.exception.ApiException;
import com.parroto.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SocialLoginAndPasswordTest {

    @Test
    void googleLoginWithoutClientIdIs503AndNeverCallsGoogle() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GoogleTokenVerifier verifier = new GoogleTokenVerifier(restTemplate, "");

        assertThatThrownBy(() -> verifier.verify("any-token"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getStatus())
                                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void appleLoginWithoutClientIdIs503() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AppleTokenVerifier verifier = new AppleTokenVerifier(restTemplate, " ");

        assertThatThrownBy(() -> verifier.verify("a.b.c"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getStatus())
                                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void changePasswordOnGoogleAccountIs400() {
        UserRepository repository = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UUID id = UUID.randomUUID();
        // Even if a hash somehow exists, a social account must not change password here.
        User user = User.builder().id(id).email("g@example.com").authProvider("google")
                .passwordHash("hash").isActive(true).build();
        when(repository.findById(id)).thenReturn(Optional.of(user));
        ProfileService service = new ProfileService(repository, encoder, "");

        assertThatThrownBy(() -> service.changePassword(id, "old", "new-password"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getStatus())
                                .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(repository, never()).save(any());
    }

    @Test
    void changePasswordOnEmailAccountStillWorks() {
        UserRepository repository = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).email("e@example.com").authProvider("email")
                .passwordHash("hash").isActive(true).build();
        when(repository.findById(id)).thenReturn(Optional.of(user));
        when(encoder.matches("old", "hash")).thenReturn(true);
        when(encoder.matches("new-password", "hash")).thenReturn(false);
        when(encoder.encode("new-password")).thenReturn("new-hash");
        ProfileService service = new ProfileService(repository, encoder, "");

        service.changePassword(id, "old", "new-password");

        verify(repository).save(user);
    }
}
