package com.parroto.vocabulary.service.dictionary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parroto.common.cache.CacheService;
import com.parroto.common.exception.ApiException;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.vocabulary.dto.DictionaryResponse;
import com.parroto.vocabulary.dto.DictionaryResponse.DefinitionEn;
import com.parroto.vocabulary.dto.DictionaryResponse.Example;
import com.parroto.vocabulary.dto.DictionarySuggestion;
import com.parroto.vocabulary.entity.VocabCard;
import com.parroto.vocabulary.repository.VocabCardRepository;
import com.parroto.vocabulary.service.dictionary.FreeDictionaryClient.Lookup;
import com.parroto.vocabulary.service.dictionary.FreeDictionaryClient.Outcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * P2-C word lookup: own + public cards first (Vietnamese meaning, IPA, example, audio), then the
 * Free Dictionary API (cached in Redis: hits 7 days, misses 1 day). An unreachable API or Redis
 * never fails the request; the response then carries whatever is known.
 */
@Service
public class DictionaryService {

    private static final Logger log = LoggerFactory.getLogger(DictionaryService.class);

    static final Duration HIT_TTL = Duration.ofDays(7);
    static final Duration MISS_TTL = Duration.ofDays(1);
    static final String CACHE_PREFIX = "vocab:dict:ext:v1:";
    static final String TRANSLATION_CACHE_PREFIX = "vocab:dict:vi:v1:";
    static final String MISS_MARKER = "{\"miss\":true}";
    /** At most this many uncached API calls per lookup (each lemma guess costs one). */
    static final int MAX_EXTERNAL_CALLS = 2;
    static final int MAX_MEANINGS = 5;
    static final int MAX_EXAMPLES = 3;
    private static final UUID NOBODY = new UUID(0L, 0L);
    /** After a failed API call, lookups skip the API for this long instead of each waiting for a timeout. */
    static final Duration COOL_DOWN = Duration.ofSeconds(30);

    private final java.util.concurrent.atomic.AtomicLong externalBlockedUntil = new java.util.concurrent.atomic.AtomicLong();
    private java.time.Clock clock = java.time.Clock.systemUTC();

    private final VocabCardRepository cardRepository;
    private final FreeDictionaryClient client;
    private final TranslationClient translationClient;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public DictionaryService(VocabCardRepository cardRepository, FreeDictionaryClient client,
                             TranslationClient translationClient,
                             CacheService cacheService, ObjectMapper objectMapper) {
        this.cardRepository = cardRepository;
        this.client = client;
        this.translationClient = translationClient;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    /** Test-friendly constructor retained for dictionary-only unit tests. */
    DictionaryService(VocabCardRepository cardRepository, FreeDictionaryClient client,
                      CacheService cacheService, ObjectMapper objectMapper) {
        this(cardRepository, client, null, cacheService, objectMapper);
    }

    @Transactional(readOnly = true)
    public DictionaryResponse lookup(AuthenticatedUser principal, String rawWord) {
        String query = WordNormalizer.normalize(rawWord);
        if (!WordNormalizer.isValid(query)) {
            throw ApiException.badRequest("Từ không hợp lệ");
        }
        List<String> candidates = WordNormalizer.candidates(query);
        UUID userId = principal != null ? principal.userUuid() : NOBODY;
        List<VocabCard> cards = cardRepository.findForDictionary(candidates, userId, PageRequest.of(0, 50));

        // First candidate (the word itself before lemma guesses) that a card knows.
        Map<String, List<VocabCard>> byWord = cards.stream()
                .collect(Collectors.groupingBy(c -> c.getWord().trim().toLowerCase(java.util.Locale.ROOT)));
        String resolved = candidates.stream().filter(byWord::containsKey).findFirst().orElse(null);
        List<VocabCard> matched = resolved == null ? List.of() : byWord.get(resolved);

        // External: the word the cards resolved to, otherwise the candidates in order.
        List<String> externalWords = resolved != null ? List.of(resolved) : candidates;
        ExternalResult ext = external(externalWords);
        if (resolved == null && ext.entry() != null) {
            resolved = ext.word();
        }
        String translation = matched.stream().noneMatch(c -> c.getMeaning() != null && !c.getMeaning().isBlank())
                ? translateCached(resolved != null ? resolved : query) : null;
        return merge(query, resolved != null ? resolved : query, matched, ext, translation);
    }

    /** Fast local-only suggestions for the search box; deliberately does not call the external dictionary. */
    @Transactional(readOnly = true)
    public List<DictionarySuggestion> suggest(AuthenticatedUser principal, String rawQuery) {
        String prefix = WordNormalizer.normalize(rawQuery);
        if (prefix.length() < 2 || prefix.length() > 40 || prefix.contains(" ")
                || !prefix.matches("[a-z][a-z'-]*")) {
            return List.of();
        }

        UUID userId = principal != null ? principal.userUuid() : NOBODY;
        List<VocabCard> cards = cardRepository.findDictionarySuggestions(prefix, userId, PageRequest.of(0, 100));
        Map<String, SuggestionBuilder> grouped = new java.util.LinkedHashMap<>();
        for (VocabCard card : cards) {
            addSuggestionCard(grouped, card, false);
        }
        if (grouped.size() < 3 && prefix.length() >= 3) {
            int maxDistance = prefix.length() <= 5 ? 1 : 2;
            List<String> corrections = cardRepository.findDictionaryWordsByLength(Math.max(2, prefix.length() - maxDistance),
                            prefix.length() + maxDistance, userId, PageRequest.of(0, 5000)).stream()
                    .filter(candidate -> !grouped.containsKey(candidate))
                    .filter(candidate -> candidate.matches("[a-z][a-z'-]*"))
                    .map(candidate -> Map.entry(candidate, editDistance(prefix, candidate)))
                    .filter(entry -> entry.getValue() > 0 && entry.getValue() <= maxDistance)
                    .sorted((left, right) -> {
                        int byDistance = Integer.compare(left.getValue(), right.getValue());
                        return byDistance != 0 ? byDistance : left.getKey().compareTo(right.getKey());
                    })
                    .limit(8 - grouped.size()).map(Map.Entry::getKey).toList();
            if (!corrections.isEmpty()) {
                for (VocabCard card : cardRepository.findDictionaryCardsByWords(corrections, userId, PageRequest.of(0, 100))) {
                    addSuggestionCard(grouped, card, true);
                }
            }
        }
        return grouped.values().stream().limit(8)
                .map(item -> new DictionarySuggestion(item.word, List.copyOf(item.meanings),
                        List.copyOf(item.partsOfSpeech), item.correction))
                .toList();
    }

    private static void addSuggestionCard(Map<String, SuggestionBuilder> grouped, VocabCard card, boolean correction) {
        String word = card.getWord() == null ? "" : card.getWord().trim();
        if (word.isEmpty()) return;
        SuggestionBuilder builder = grouped.computeIfAbsent(word.toLowerCase(java.util.Locale.ROOT),
                ignored -> new SuggestionBuilder(word, correction));
        if (card.getMeaning() != null && !card.getMeaning().isBlank() && builder.meanings.size() < 4) {
            builder.meanings.add(card.getMeaning().trim());
        }
        if (card.getPartOfSpeech() != null && !card.getPartOfSpeech().isBlank()) {
            builder.partsOfSpeech.add(card.getPartOfSpeech().trim());
        }
    }

    private static int editDistance(String left, String right) {
        int[][] distance = new int[left.length() + 1][right.length() + 1];
        for (int i = 0; i <= left.length(); i++) distance[i][0] = i;
        for (int j = 0; j <= right.length(); j++) distance[0][j] = j;
        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                distance[i][j] = Math.min(Math.min(distance[i - 1][j] + 1, distance[i][j - 1] + 1),
                        distance[i - 1][j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1));
                if (i > 1 && j > 1 && left.charAt(i - 1) == right.charAt(j - 2)
                        && left.charAt(i - 2) == right.charAt(j - 1)) {
                    distance[i][j] = Math.min(distance[i][j], distance[i - 2][j - 2] + 1);
                }
            }
        }
        return distance[left.length()][right.length()];
    }

    private static final class SuggestionBuilder {
        private final String word;
        private final boolean correction;
        private final Set<String> meanings = new LinkedHashSet<>();
        private final Set<String> partsOfSpeech = new LinkedHashSet<>();

        private SuggestionBuilder(String word, boolean correction) {
            this.word = word;
            this.correction = correction;
        }
    }

    void setClock(java.time.Clock clock) {
        this.clock = clock;
    }

    record ExternalResult(String word, ExternalDictionaryEntry entry, String status) {
    }

    ExternalResult external(List<String> words) {
        if (!client.enabled()) return new ExternalResult(null, null, "disabled");
        int calls = 0;
        boolean failed = false;
        for (String w : words) {
            String cached = cacheGet(CACHE_PREFIX + w);
            if (cached != null) {
                if (MISS_MARKER.equals(cached)) continue;
                ExternalDictionaryEntry e = fromJson(cached);
                if (e != null) return new ExternalResult(w, e, "ok");
            }
            if (calls >= MAX_EXTERNAL_CALLS) {
                break;
            }
            if (clock.millis() < externalBlockedUntil.get()) {
                return new ExternalResult(null, null, "skipped");
            }
            calls++;
            Lookup lookup = client.lookup(w);
            if (lookup.outcome() == Outcome.FOUND) {
                cachePut(CACHE_PREFIX + w, toJson(lookup.entry()), HIT_TTL);
                return new ExternalResult(w, lookup.entry(), "ok");
            }
            if (lookup.outcome() == Outcome.MISS) {
                cachePut(CACHE_PREFIX + w, MISS_MARKER, MISS_TTL);
            } else {
                // Not cached; the API is likely down or slow: stop here and let it rest for a while.
                failed = true;
                externalBlockedUntil.set(clock.millis() + COOL_DOWN.toMillis());
                break;
            }
        }
        return new ExternalResult(null, null, failed ? "error" : "miss");
    }

    private String translateCached(String text) {
        if (translationClient == null) return null;
        String key = TRANSLATION_CACHE_PREFIX + text.toLowerCase(java.util.Locale.ROOT);
        String cached = cacheGet(key);
        if (cached != null) return MISS_MARKER.equals(cached) ? null : cached;
        String translated = translationClient.translate(text);
        cachePut(key, translated != null ? translated : MISS_MARKER,
                translated != null ? HIT_TTL : MISS_TTL);
        return translated;
    }

    static DictionaryResponse merge(String query, String word, List<VocabCard> cards, ExternalResult ext,
                                    String translatedMeaning) {
        ExternalDictionaryEntry e = ext.entry();
        String ipa = cards.stream().map(VocabCard::getIpa).map(FreeDictionaryClient::stripSlashes)
                .filter(s -> s != null && !s.isBlank()).findFirst()
                .orElse(e != null ? e.ipa() : null);
        String audio = cards.stream().map(VocabCard::getAudioUrl).filter(s -> s != null && !s.isBlank())
                .findFirst().orElse(e != null ? e.audioUrl() : null);

        Set<String> meanings = new LinkedHashSet<>();
        Set<String> pos = new LinkedHashSet<>();
        List<Example> examples = new ArrayList<>();
        Set<String> seenExamples = new LinkedHashSet<>();
        for (VocabCard c : cards) {
            if (c.getMeaning() != null && !c.getMeaning().isBlank() && meanings.size() < MAX_MEANINGS) {
                meanings.add(c.getMeaning().trim());
            }
            if (c.getPartOfSpeech() != null && !c.getPartOfSpeech().isBlank()) pos.add(c.getPartOfSpeech().trim());
            if (c.getExample() != null && !c.getExample().isBlank() && examples.size() < MAX_EXAMPLES
                    && seenExamples.add(c.getExample().trim().toLowerCase(java.util.Locale.ROOT))) {
                examples.add(new Example(c.getExample().trim(), blankToNull(c.getExampleVi())));
            }
        }
        if (meanings.isEmpty() && translatedMeaning != null && !translatedMeaning.isBlank()) {
            meanings.add(translatedMeaning.trim());
        }
        List<DefinitionEn> defs = new ArrayList<>();
        if (e != null && e.definitions() != null) {
            for (ExternalDictionaryEntry.Definition d : e.definitions()) {
                defs.add(new DefinitionEn(d.partOfSpeech(), d.definition(), d.example()));
                if (d.partOfSpeech() != null) pos.add(d.partOfSpeech());
            }
        }
        boolean fromCards = !cards.isEmpty();
        boolean fromDict = e != null;
        boolean fromTranslation = translatedMeaning != null && !translatedMeaning.isBlank();
        String source = fromTranslation
                ? fromCards && fromDict ? "cards+dictionary+translation" : fromCards ? "cards+translation"
                : fromDict ? "dictionary+translation" : "translation"
                : fromCards && fromDict ? "cards+dictionary" : fromCards ? "cards" : fromDict ? "dictionary" : "none";
        return new DictionaryResponse(query, word, fromCards || fromDict || fromTranslation, ipa, audio, List.copyOf(meanings),
                List.copyOf(pos), defs, examples, source, ext.status());
    }

    private String cacheGet(String key) {
        try {
            return cacheService.get(key, String.class);
        } catch (DataAccessException ex) {
            log.warn("Dictionary cache read failed: {}", ex.getMessage());
            return null;
        }
    }

    private void cachePut(String key, String value, Duration ttl) {
        if (value == null) return;
        try {
            cacheService.set(key, value, ttl);
        } catch (DataAccessException ex) {
            log.warn("Dictionary cache write failed: {}", ex.getMessage());
        }
    }

    private String toJson(ExternalDictionaryEntry e) {
        try {
            return objectMapper.writeValueAsString(e);
        } catch (Exception ex) {
            return null;
        }
    }

    private ExternalDictionaryEntry fromJson(String json) {
        try {
            return objectMapper.readValue(json, ExternalDictionaryEntry.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
