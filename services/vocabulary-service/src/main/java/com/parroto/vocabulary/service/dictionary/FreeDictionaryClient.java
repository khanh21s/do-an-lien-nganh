package com.parroto.vocabulary.service.dictionary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * P2-C: client of the free Free Dictionary API ({@code https://api.dictionaryapi.dev}, English
 * definitions, IPA, audio). Own RestTemplate with short timeouts so a slow upstream never holds a
 * request for long. Never throws: the outcome is {@link Lookup#found}, {@link Lookup#miss} (HTTP 404)
 * or {@link Lookup#failed} (anything else).
 */
@Component
public class FreeDictionaryClient {

    private static final Logger log = LoggerFactory.getLogger(FreeDictionaryClient.class);
    static final int MAX_DEFINITIONS = 10;

    public enum Outcome { FOUND, MISS, FAILED, DISABLED }

    public record Lookup(Outcome outcome, ExternalDictionaryEntry entry) {
        static Lookup found(ExternalDictionaryEntry e) {
            return new Lookup(Outcome.FOUND, e);
        }

        static Lookup miss() {
            return new Lookup(Outcome.MISS, null);
        }

        static Lookup failed() {
            return new Lookup(Outcome.FAILED, null);
        }
    }

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final boolean enabled;

    @org.springframework.beans.factory.annotation.Autowired
    public FreeDictionaryClient(RestTemplateBuilder builder, ObjectMapper objectMapper,
                                @Value("${dictionary.api-url:https://api.dictionaryapi.dev/api/v2/entries/en}") String baseUrl,
                                @Value("${dictionary.external-enabled:true}") boolean enabled,
                                @Value("${dictionary.connect-timeout-ms:1500}") long connectTimeoutMs,
                                @Value("${dictionary.read-timeout-ms:2500}") long readTimeoutMs) {
        this(builder.setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs)).build(), objectMapper, baseUrl, enabled);
    }

    FreeDictionaryClient(RestTemplate restTemplate, ObjectMapper objectMapper, String baseUrl, boolean enabled) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public Lookup lookup(String word) {
        if (!enabled) return new Lookup(Outcome.DISABLED, null);
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl).pathSegment(word).build().encode().toUri();
        try {
            String body = restTemplate.getForObject(uri, String.class);
            ExternalDictionaryEntry entry = parse(word, body);
            return entry == null ? Lookup.miss() : Lookup.found(entry);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) return Lookup.miss();
            log.warn("Dictionary API {} for '{}'", e.getStatusCode().value(), word);
            return Lookup.failed();
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("Dictionary API unreachable for '{}': {}", word, e.getMessage());
            return Lookup.failed();
        }
    }

    /** Parses the API's JSON array; null when it has no usable definition. */
    ExternalDictionaryEntry parse(String word, String body) {
        if (body == null || body.isBlank()) return null;
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("Dictionary API returned invalid JSON for '{}'", word);
            return null;
        }
        if (!root.isArray() || root.isEmpty()) return null;
        String ipa = null;
        String audio = null;
        String audioAny = null;
        List<ExternalDictionaryEntry.Definition> defs = new ArrayList<>();
        for (JsonNode entry : root) {
            if (ipa == null) ipa = text(entry, "phonetic");
            for (JsonNode ph : entry.path("phonetics")) {
                if (ipa == null) ipa = text(ph, "text");
                String a = text(ph, "audio");
                if (a != null && a.startsWith("http")) {
                    if (audioAny == null) audioAny = a;
                    if (audio == null && a.contains("-us.")) audio = a;
                }
            }
            for (JsonNode meaning : entry.path("meanings")) {
                String pos = text(meaning, "partOfSpeech");
                for (JsonNode d : meaning.path("definitions")) {
                    String def = text(d, "definition");
                    if (def != null && defs.size() < MAX_DEFINITIONS) {
                        defs.add(new ExternalDictionaryEntry.Definition(pos, def, text(d, "example")));
                    }
                }
            }
        }
        if (defs.isEmpty() && ipa == null) return null;
        String w = text(root.get(0), "word");
        return new ExternalDictionaryEntry(w != null ? w : word, stripSlashes(ipa), audio != null ? audio : audioAny,
                defs);
    }

    static String stripSlashes(String ipa) {
        if (ipa == null) return null;
        String s = ipa.trim().replaceAll("^[/\\[]+|[/\\]]+$", "").trim();
        return s.isEmpty() ? null : s;
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        if (v == null || !v.isTextual()) return null;
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }
}
