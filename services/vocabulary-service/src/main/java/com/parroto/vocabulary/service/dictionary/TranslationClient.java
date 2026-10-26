package com.parroto.vocabulary.service.dictionary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;

/** Optional EN→VI fallback for words and short phrases without a Vietnamese card meaning. */
@Component
public class TranslationClient {
    private static final Logger log = LoggerFactory.getLogger(TranslationClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final boolean enabled;

    public TranslationClient(RestTemplateBuilder builder, ObjectMapper objectMapper,
                             @Value("${dictionary.translation-api-url:https://api.mymemory.translated.net/get}") String endpoint,
                             @Value("${dictionary.translation-enabled:true}") boolean enabled,
                             @Value("${dictionary.translation-connect-timeout-ms:1000}") long connectTimeoutMs,
                             @Value("${dictionary.translation-read-timeout-ms:1800}") long readTimeoutMs) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs)).build();
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.enabled = enabled;
    }

    public String translate(String text) {
        if (!enabled || text == null || text.isBlank() || text.length() > 200) return null;
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(endpoint)
                    .queryParam("q", text).queryParam("langpair", "en|vi").build().encode().toUri();
            String body = restTemplate.getForObject(uri, String.class);
            JsonNode node = objectMapper.readTree(body).path("responseData").path("translatedText");
            if (!node.isTextual()) return null;
            String translation = node.asText().trim();
            return translation.isBlank() || translation.equalsIgnoreCase(text.trim()) ? null : translation;
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("English-Vietnamese translation unavailable: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Could not parse English-Vietnamese translation response: {}", e.getMessage());
            return null;
        }
    }
}
