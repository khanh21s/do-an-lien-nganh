package com.parroto.practice.service;

import com.parroto.common.cache.CacheService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** P2-A1: same Redis key/TTL as vocabulary-service so subscription-service's eviction applies. */
class PremiumClientTest {

    private final RestTemplate rest = mock(RestTemplate.class);
    private final CacheService cache = mock(CacheService.class);
    private final PremiumClient client = new PremiumClient(rest, "http://subscription:8005", cache, "tok");

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void missCallsInternalEndpointWithTokenAndCachesFiveMinutes() {
        when(rest.exchange(eq("http://subscription:8005/internal/subscriptions/{userId}/active"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class), eq("u1")))
                .thenReturn((ResponseEntity) ResponseEntity.ok(Map.of("isPremium", true)));

        assertThat(client.lookup("u1")).contains(true);

        verify(rest).exchange(anyString(), eq(HttpMethod.GET),
                argThat((HttpEntity<?> e) -> "tok".equals(e.getHeaders().getFirst("X-Internal-Token"))),
                eq(Map.class), eq("u1"));
        verify(cache).set("premium:u1", true, Duration.ofMinutes(5));
    }

    @Test
    void hitSkipsTheCall() {
        when(cache.get("premium:u2", Boolean.class)).thenReturn(false);
        assertThat(client.lookup("u2")).contains(false);
        verifyNoInteractions(rest);
    }

    @Test
    @SuppressWarnings("unchecked")
    void failureIsEmptyAndNotCached() {
        when(rest.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class), anyString()))
                .thenThrow(new ResourceAccessException("down"));
        assertThat(client.lookup("u3")).isEqualTo(Optional.empty());
        verify(cache, never()).set(anyString(), any(), any());
    }
}
