package com.parroto.practice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parroto.practice.service.speech.AzureSpeechClient;
import com.parroto.practice.service.speech.MockPronunciationScorer;
import com.parroto.practice.service.speech.PronunciationScorer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/** Creates exactly one {@link PronunciationScorer} bean according to {@code app.speech.provider}. */
@Configuration
public class SpeechConfig {

    @Bean
    public PronunciationScorer pronunciationScorer(@Value("${app.speech.provider:auto}") String provider,
                                                   @Value("${azure.speech.key:}") String speechKey,
                                                   @Value("${azure.speech.region:eastus}") String speechRegion,
                                                   RestTemplate restTemplate, ObjectMapper objectMapper) {
        String resolved = ProviderSelection.resolveSpeech(provider, speechKey);
        if (ProviderSelection.SPEECH_AZURE.equals(resolved)) {
            return new AzureSpeechClient(restTemplate, objectMapper, speechKey, speechRegion);
        }
        return new MockPronunciationScorer();
    }
}
