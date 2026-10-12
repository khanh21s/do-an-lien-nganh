package com.parroto.practice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parroto.practice.dto.PhonemeScoreDto;
import com.parroto.practice.dto.ShadowingWordScoreDto;
import com.parroto.practice.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Calls Azure Cognitive Services' Pronunciation Assessment REST API directly
 * (see plan: avoids the native client-sdk, which doesn't run on Alpine/musl).
 * https://learn.microsoft.com/azure/ai-services/speech-service/rest-speech-to-text-short
 *
 * Audio must be 16kHz mono PCM WAV, as required by the short-audio recognition endpoint;
 * the frontend recorder is responsible for producing that format before uploading.
 */
@Service
public class AzureSpeechClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String speechKey;
    private final String speechRegion;

    public AzureSpeechClient(RestTemplate restTemplate, ObjectMapper objectMapper,
                              @Value("${azure.speech.key}") String speechKey,
                              @Value("${azure.speech.region}") String speechRegion) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.speechKey = speechKey;
        this.speechRegion = speechRegion;
    }

    public record AssessmentResult(double overallAccuracy, List<ShadowingWordScoreDto> wordScores) {
    }

    public AssessmentResult assessPronunciation(byte[] audioBytes, String referenceText) {
        if (speechKey == null || speechKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Azure Speech is not configured (AZURE_SPEECH_KEY missing)");
        }
        String url = "https://%s.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1"
                .formatted(speechRegion) + "?language=en-US&format=detailed";

        String pronunciationConfig = buildPronunciationAssessmentHeader(referenceText);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Ocp-Apim-Subscription-Key", speechKey);
        headers.set("Pronunciation-Assessment", pronunciationConfig);
        headers.setContentType(MediaType.parseMediaType("audio/wav; codecs=audio/pcm; samplerate=16000"));
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<byte[]> entity = new HttpEntity<>(audioBytes, headers);
        JsonNode response;
        try {
            response = restTemplate.postForObject(url, entity, JsonNode.class);
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Azure pronunciation assessment call failed: " + e.getMessage());
        }
        if (response == null || !response.has("NBest") || response.get("NBest").isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Azure returned no recognition result");
        }
        JsonNode best = response.get("NBest").get(0);
        double overall = best.path("PronunciationAssessment").path("AccuracyScore").asDouble(0);

        List<ShadowingWordScoreDto> wordScores = new ArrayList<>();
        for (JsonNode wordNode : best.path("Words")) {
            String word = wordNode.path("Word").asText();
            double wordAccuracy = wordNode.path("PronunciationAssessment").path("AccuracyScore").asDouble(0);
            List<PhonemeScoreDto> phonemes = new ArrayList<>();
            for (JsonNode phonemeNode : wordNode.path("Phonemes")) {
                phonemes.add(new PhonemeScoreDto(
                        phonemeNode.path("Phoneme").asText(),
                        phonemeNode.path("PronunciationAssessment").path("AccuracyScore").asDouble(0)));
            }
            wordScores.add(new ShadowingWordScoreDto(word, phonemes, wordAccuracy, statusFor(wordAccuracy)));
        }
        return new AssessmentResult(overall, wordScores);
    }

    private String statusFor(double accuracy) {
        if (accuracy >= 80) return "green";
        if (accuracy >= 60) return "yellow";
        return "red";
    }

    private String buildPronunciationAssessmentHeader(String referenceText) {
        var config = objectMapper.createObjectNode();
        config.put("ReferenceText", referenceText);
        config.put("GradingSystem", "HundredMark");
        config.put("Granularity", "Phoneme");
        config.put("EnableMiscue", true);
        try {
            byte[] json = objectMapper.writeValueAsBytes(config);
            return Base64.getEncoder().encodeToString(json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Azure pronunciation config", e);
        }
    }
}
