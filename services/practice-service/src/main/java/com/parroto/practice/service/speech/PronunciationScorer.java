package com.parroto.practice.service.speech;

import com.parroto.practice.dto.ShadowingWordScoreDto;

import java.util.List;

/**
 * Pronunciation assessment backend (architecture §8.2): Azure Speech when a key is
 * configured, otherwise the deterministic in-process {@link MockPronunciationScorer}.
 * Callers must pass a WAV already checked by {@code WavUtils.validate}.
 */
public interface PronunciationScorer {

    /**
     * @param overallAccuracy 0–100
     * @param scorer          "azure" | "mock"
     */
    record AssessmentResult(double overallAccuracy, List<ShadowingWordScoreDto> wordScores, String scorer) {
    }

    /** "azure" or "mock". */
    String name();

    /**
     * @param seed stable per-recording value (the recordingId); only the mock scorer uses it
     */
    AssessmentResult assess(byte[] wav, String referenceText, String seed);

    default AssessmentResult assess(byte[] wav, String referenceText) {
        return assess(wav, referenceText, "");
    }

    /** Colour bucket shared by both scorers: ≥80 green, 60–79 yellow, <60 red. */
    static String statusFor(double accuracy) {
        if (accuracy >= 80) return "green";
        if (accuracy >= 60) return "yellow";
        return "red";
    }
}
