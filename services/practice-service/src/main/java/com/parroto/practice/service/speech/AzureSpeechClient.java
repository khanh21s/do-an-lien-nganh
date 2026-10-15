package com.parroto.practice.service.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parroto.practice.dto.PhonemeScoreDto;
import com.parroto.practice.dto.ShadowingWordScoreDto;
import com.parroto.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * Calls Azure Cognitive Services' Pronunciation Assessment REST API directly
 * (avoids the native client-sdk, which doesn't run on Alpine/musl).
 * https://learn.microsoft.com/azure/ai-services/speech-service/rest-speech-to-text-short
 *
 * Audio must be 16 kHz mono 16-bit PCM WAV, as required by the short-audio endpoint; the
 * frontend recorder produces that format and {@code WavUtils.validate} enforces it before
 * this client is called (SHA-02).
 */
public class AzureSpeechClient implements PronunciationScorer {

    private static final Logger log = LoggerFactory.getLogger(AzureSpeechClient.class);
    static final String AUDIO_CONTENT_TYPE = "audio/wav; codecs=audio/pcm; samplerate=16000";
    private static final Set<String> NO_SPEECH_STATUSES = Set.of("NoMatch", "InitialSilenceTimeout", "BabbleTimeout");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String speechKey;
    private final String speechRegion;

    public AzureSpeechClient(RestTemplate restTemplate, ObjectMapper objectMapper,
                             String speechKey, String speechRegion) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.speechKey = speechKey == null ? "" : speechKey.trim();
        this.speechRegion = speechRegion;
    }

    @Override
    public String name() {
        return "azure";
    }

    public String region() {
        return speechRegion;
    }

    @Override
    public AssessmentResult assess(byte[] audioBytes, String referenceText, String seed) {
        if (speechKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Azure Speech is not configured (AZURE_SPEECH_KEY missing)");
        }
        String url = "https://%s.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1"
                .formatted(speechRegion) + "?language=en-US&format=detailed";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Ocp-Apim-Subscription-Key", speechKey);
        headers.set("Pronunciation-Assessment", buildPronunciationAssessmentHeader(referenceText));
        headers.setContentType(MediaType.parseMediaType(AUDIO_CONTENT_TYPE));
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<byte[]> entity = new HttpEntity<>(audioBytes, headers);
        JsonNode response;
        try {
            response = restTemplate.postForObject(url, entity, JsonNode.class);
        } catch (RestClientException e) {
            log.warn("Azure pronunciation assessment call failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Azure pronunciation assessment call failed");
        }
        if (response != null && NO_SPEECH_STATUSES.contains(response.path("RecognitionStatus").asText())) {
            throw ApiException.badRequest("Không nhận diện được giọng nói trong bản ghi, hãy thử lại");
        }
        if (response == null || !response.has("NBest") || response.get("NBest").isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Azure returned no recognition result");
        }
        return parse(response);
    }

    AssessmentResult parse(JsonNode response) {
        JsonNode best = response.get("NBest").get(0);
        // Newer API versions put the scores directly on the NBest item instead of a nested object.
        double overall = score(best);

        List<ShadowingWordScoreDto> wordScores = new ArrayList<>();
        for (JsonNode wordNode : best.path("Words")) {
            String word = wordNode.path("Word").asText();
            double wordAccuracy = score(wordNode);
            List<PhonemeScoreDto> phonemes = new ArrayList<>();
            for (JsonNode phonemeNode : wordNode.path("Phonemes")) {
                phonemes.add(new PhonemeScoreDto(phonemeNode.path("Phoneme").asText(), score(phonemeNode)));
            }
            wordScores.add(new ShadowingWordScoreDto(word, phonemes, wordAccuracy,
                    PronunciationScorer.statusFor(wordAccuracy)));
        }
        return new AssessmentResult(overall, wordScores, name());
    }

    private static double score(JsonNode node) {
        JsonNode nested = node.path("PronunciationAssessment").path("AccuracyScore");
        return nested.isMissingNode() ? node.path("AccuracyScore").asDouble(0) : nested.asDouble(0);
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
