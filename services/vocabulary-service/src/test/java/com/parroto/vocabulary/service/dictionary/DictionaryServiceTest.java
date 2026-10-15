package com.parroto.vocabulary.service.dictionary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parroto.common.cache.CacheService;
import com.parroto.common.exception.ApiException;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.vocabulary.dto.DictionaryResponse;
import com.parroto.vocabulary.entity.VocabCard;
import com.parroto.vocabulary.entity.VocabDeck;
import com.parroto.vocabulary.repository.VocabCardRepository;
import com.parroto.vocabulary.service.dictionary.FreeDictionaryClient.Lookup;
import com.parroto.vocabulary.service.dictionary.FreeDictionaryClient.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DictionaryServiceTest {

    private VocabCardRepository cards;
    private FreeDictionaryClient client;
    private CacheService cache;
    private DictionaryService service;
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID userId = UUID.randomUUID();

    private static final ExternalDictionaryEntry RUN = new ExternalDictionaryEntry("run", "rʌn",
            "https://x/run-us.mp3", List.of(new ExternalDictionaryEntry.Definition("verb", "Move fast on foot.", "I run.")));

    @BeforeEach
    void setUp() {
        cards = mock(VocabCardRepository.class);
        client = mock(FreeDictionaryClient.class);
        cache = mock(CacheService.class);
        when(client.enabled()).thenReturn(true);
        when(cards.findForDictionary(any(), any(), any())).thenReturn(List.of());
        service = new DictionaryService(cards, client, cache, mapper);
    }

    private VocabCard card(String word, String meaning, String ipa, UUID owner) {
        VocabDeck deck = VocabDeck.builder().id(UUID.randomUUID()).name("d").ownerId(owner).build();
        return VocabCard.builder().id(UUID.randomUUID()).deck(deck).word(word).meaning(meaning).ipa(ipa)
                .example("I " + word + " every day.").exampleVi("Tôi … mỗi ngày.").partOfSpeech("verb").build();
    }

    private AuthenticatedUser me() {
        return new AuthenticatedUser(userId.toString(), "me@x.com", "A1");
    }

    @Test
    void cardsAndDictionaryAreMergedOwnCardFirst() {
        when(cards.findForDictionary(eq(List.of("run")), eq(userId), any()))
                .thenReturn(List.of(card("run", "chạy (của tôi)", null, userId), card("Run", "chạy", "/rʌn/", null)));
        when(client.lookup("run")).thenReturn(new Lookup(Outcome.FOUND, RUN));

        DictionaryResponse r = service.lookup(me(), "Run!");

        assertThat(r.query()).isEqualTo("run");
        assertThat(r.word()).isEqualTo("run");
        assertThat(r.found()).isTrue();
        assertThat(r.meaningsVi()).containsExactly("chạy (của tôi)", "chạy");
        assertThat(r.ipa()).isEqualTo("rʌn"); // first card with an IPA, slashes removed
        assertThat(r.audioUrl()).isEqualTo("https://x/run-us.mp3");
        assertThat(r.definitionsEn()).extracting(DictionaryResponse.DefinitionEn::definition)
                .containsExactly("Move fast on foot.");
        assertThat(r.examples()).hasSize(1); // same example sentence only once
        assertThat(r.source()).isEqualTo("cards+dictionary");
        assertThat(r.externalStatus()).isEqualTo("ok");
        verify(cache).set(eq("vocab:dict:ext:v1:run"), anyString(), eq(Duration.ofDays(7)));
    }

    @Test
    void lemmaFallbackResolvesToTheCardWord() {
        when(cards.findForDictionary(any(), any(), any())).thenReturn(List.of(card("run", "chạy", null, null)));
        when(client.lookup("run")).thenReturn(new Lookup(Outcome.MISS, null));

        DictionaryResponse r = service.lookup(null, "running");

        assertThat(r.query()).isEqualTo("running");
        assertThat(r.word()).isEqualTo("run");
        assertThat(r.source()).isEqualTo("cards");
        verify(client).lookup("run");
        verify(client, never()).lookup("running");
        verify(cache).set("vocab:dict:ext:v1:run", DictionaryService.MISS_MARKER, Duration.ofDays(1));
    }

    @Test
    void cacheHitSkipsTheApi() throws Exception {
        when(cache.get("vocab:dict:ext:v1:run", String.class)).thenReturn(mapper.writeValueAsString(RUN));

        DictionaryResponse r = service.lookup(null, "run");

        assertThat(r.source()).isEqualTo("dictionary");
        assertThat(r.definitionsEn()).hasSize(1);
        verify(client, never()).lookup(anyString());
    }

    @Test
    void cachedMissMovesToTheNextCandidate() throws Exception {
        when(cache.get("vocab:dict:ext:v1:cats", String.class)).thenReturn(DictionaryService.MISS_MARKER);
        when(client.lookup("cat")).thenReturn(new Lookup(Outcome.FOUND,
                new ExternalDictionaryEntry("cat", "kæt", null, List.of())));

        DictionaryResponse r = service.lookup(null, "cats");

        assertThat(r.word()).isEqualTo("cat");
        verify(client, never()).lookup("cats");
    }

    @Test
    void externalFailureStillReturnsCardsAndIsNotCached() {
        when(cards.findForDictionary(any(), any(), any())).thenReturn(List.of(card("hello", "xin chào", "/həˈloʊ/", null)));
        when(client.lookup("hello")).thenReturn(new Lookup(Outcome.FAILED, null));

        DictionaryResponse r = service.lookup(null, "hello");

        assertThat(r.found()).isTrue();
        assertThat(r.meaningsVi()).containsExactly("xin chào");
        assertThat(r.externalStatus()).isEqualTo("error");
        assertThat(r.definitionsEn()).isEmpty();
        verify(cache, never()).set(anyString(), any(), any());
    }

    @Test
    void afterAFailureTheApiRestsForAWhile() {
        java.time.Instant t0 = java.time.Instant.parse("2026-10-15T00:00:00Z");
        service.setClock(java.time.Clock.fixed(t0, java.time.ZoneOffset.UTC));
        when(client.lookup(anyString())).thenReturn(new Lookup(Outcome.FAILED, null));

        assertThat(service.lookup(null, "stopped").externalStatus()).isEqualTo("error");
        verify(client, times(1)).lookup(anyString()); // no second guess once the API failed

        assertThat(service.lookup(null, "apple").externalStatus()).isEqualTo("skipped");
        verify(client, times(1)).lookup(anyString());

        service.setClock(java.time.Clock.fixed(t0.plus(DictionaryService.COOL_DOWN).plusSeconds(1),
                java.time.ZoneOffset.UTC));
        when(client.lookup("apple")).thenReturn(new Lookup(Outcome.MISS, null));
        assertThat(service.lookup(null, "apple").externalStatus()).isEqualTo("miss");
    }

    @Test
    void nothingKnownIsNotAnError() {
        when(client.lookup(anyString())).thenReturn(new Lookup(Outcome.MISS, null));
        DictionaryResponse r = service.lookup(null, "qwzx");
        assertThat(r.found()).isFalse();
        assertThat(r.source()).isEqualTo("none");
        assertThat(r.externalStatus()).isEqualTo("miss");
    }

    @Test
    void externalCallsPerLookupAreCapped() {
        when(client.lookup(anyString())).thenReturn(new Lookup(Outcome.MISS, null));
        service.lookup(null, "stopped"); // stopped, stopp, stoppe, stop
        verify(client, times(DictionaryService.MAX_EXTERNAL_CALLS)).lookup(anyString());
    }

    @Test
    void redisDownIsTolerated() {
        when(cache.get(anyString(), eq(String.class))).thenThrow(new RedisConnectionFailureException("down"));
        doThrow(new RedisConnectionFailureException("down")).when(cache).set(anyString(), any(), any());
        when(client.lookup("run")).thenReturn(new Lookup(Outcome.FOUND, RUN));
        assertThat(service.lookup(null, "run").found()).isTrue();
    }

    @Test
    void invalidWordIs400() {
        assertThatThrownBy(() -> service.lookup(null, "1234")).isInstanceOf(ApiException.class)
                .hasMessage("Từ không hợp lệ");
        assertThatThrownBy(() -> service.lookup(null, "  ?! ")).isInstanceOf(ApiException.class);
        verifyNoInteractions(client);
    }

    @Test
    void guestsSearchWithAnIdThatOwnsNothing() {
        when(client.lookup(anyString())).thenReturn(new Lookup(Outcome.MISS, null));
        service.lookup(null, "apple");
        verify(cards).findForDictionary(any(), eq(new UUID(0L, 0L)), any());
    }
}
