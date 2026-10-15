package com.parroto.practice.service;

import com.parroto.common.exception.ApiException;
import com.parroto.common.security.AdminGuard;
import com.parroto.common.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** P2-A1 "preview, lock practice" gate. */
class LessonAccessGuardTest {

    private final PremiumClient premium = mock(PremiumClient.class);
    private final LessonAccessGuard guard = new LessonAccessGuard(premium, new AdminGuard("admin@parroto.com"));
    private final AuthenticatedUser free = new AuthenticatedUser(UUID.randomUUID().toString(), "free@x.vn", "B1");
    private final AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID().toString(), "Admin@Parroto.com", "B1");

    private static ContentServiceClient.LessonRef lesson(boolean pro) {
        return new ContentServiceClient.LessonRef(UUID.randomUUID(), "L", null, pro, List.of());
    }

    @Test
    void freeLessonNeverAsksSubscriptionService() {
        assertThatCode(() -> guard.requirePracticeAccess(free, lesson(false))).doesNotThrowAnyException();
        assertThat(guard.canPractice(free, false)).isTrue();
        verifyNoInteractions(premium);
    }

    @Test
    void proLessonBlocksNonPremiumWith403() {
        when(premium.lookup(free.userId())).thenReturn(Optional.of(false));

        assertThatThrownBy(() -> guard.requirePracticeAccess(free, lesson(true)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getMessage()).isEqualTo(LessonAccessGuard.PRO_REQUIRED_MESSAGE);
                });
        assertThat(guard.canPractice(free, true)).isFalse();
    }

    @Test
    void proLessonAllowsPremium() {
        when(premium.lookup(free.userId())).thenReturn(Optional.of(true));
        assertThatCode(() -> guard.requirePracticeAccess(free, lesson(true))).doesNotThrowAnyException();
        assertThat(guard.canPractice(free, true)).isTrue();
    }

    @Test
    void adminBypassesWithoutPremiumLookup() {
        assertThatCode(() -> guard.requirePracticeAccess(admin, lesson(true))).doesNotThrowAnyException();
        assertThat(guard.canPractice(admin, true)).isTrue();
        verify(premium, never()).lookup(anyString());
    }

    @Test
    void unknownPremiumStatusIs503AndLockedInListings() {
        when(premium.lookup(free.userId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> guard.requirePracticeAccess(free, lesson(true)))
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(guard.canPractice(free, true)).isFalse();
    }
}
