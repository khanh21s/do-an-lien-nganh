package com.parroto.practice.service.speech;

import com.parroto.practice.dto.PhonemeScoreDto;
import com.parroto.practice.dto.ShadowingWordScoreDto;
import com.parroto.practice.service.DictationScorer;
import com.parroto.practice.util.WavUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic, offline stand-in for Azure pronunciation assessment (architecture §8.2),
 * so the shadowing flow works end-to-end without keys. Scores come from how much of the
 * recording contains voice (20 ms frames, RMS threshold) plus a stable per-word jitter;
 * they are NOT a real pronunciation judgement, and the response is tagged {@code scorer=mock}.
 */
public class MockPronunciationScorer implements PronunciationScorer {

    static final int FRAME_SAMPLES = WavUtils.SAMPLE_RATE / 50;   // 20 ms at 16 kHz
    /** Normalised RMS (0–1) above which a frame counts as voiced (≈ -34 dBFS). */
    static final double VOICED_RMS_THRESHOLD = 0.02;
    static final double SILENCE_VOICED_RATIO = 0.10;
    private static final int MAX_PHONEME_GROUPS = 4;

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public AssessmentResult assess(byte[] wav, String referenceText, String seed) {
        WavUtils.WavInfo info = WavUtils.validate(wav);
        double voicedRatio = voicedRatio(WavUtils.samples(wav, info));
        boolean silent = voicedRatio < SILENCE_VOICED_RATIO;
        String salt = seed == null ? "" : seed;

        List<String> words = DictationScorer.normalize(referenceText);
        List<ShadowingWordScoreDto> wordScores = new ArrayList<>(words.size());
        double sum = 0;
        for (String word : words) {
            double score;
            if (silent) {
                score = 20 + Math.floorMod((word + salt).hashCode(), 21);                // 20–40
            } else {
                double base = 60 + 35 * voicedRatio;
                score = base + (Math.floorMod((word + salt).hashCode(), 15) - 7);       // ±7 jitter
            }
            score = round2(clamp(score));
            sum += score;
            wordScores.add(new ShadowingWordScoreDto(word, phonemes(word, score, salt), score,
                    PronunciationScorer.statusFor(score)));
        }
        double overall = words.isEmpty() ? 0.0 : round2(sum / words.size());
        return new AssessmentResult(overall, wordScores, name());
    }

    /** Fraction (0–1) of 20 ms frames whose RMS exceeds the voice threshold. */
    static double voicedRatio(short[] samples) {
        int frames = samples.length / FRAME_SAMPLES;
        if (frames == 0) {
            return 0.0;
        }
        int voiced = 0;
        for (int f = 0; f < frames; f++) {
            double sumSquares = 0;
            int start = f * FRAME_SAMPLES;
            for (int i = start; i < start + FRAME_SAMPLES; i++) {
                double s = samples[i] / 32768.0;
                sumSquares += s * s;
            }
            if (Math.sqrt(sumSquares / FRAME_SAMPLES) >= VOICED_RMS_THRESHOLD) {
                voiced++;
            }
        }
        return (double) voiced / frames;
    }

    /** Splits the word into at most 4 letter groups, each scored word score ± 5. */
    private List<PhonemeScoreDto> phonemes(String word, double wordScore, String salt) {
        int groups = Math.max(1, Math.min(MAX_PHONEME_GROUPS, word.length()));
        int size = (int) Math.ceil((double) word.length() / groups);
        List<PhonemeScoreDto> result = new ArrayList<>(groups);
        for (int start = 0, i = 0; start < word.length(); start += size, i++) {
            String group = word.substring(start, Math.min(word.length(), start + size));
            double delta = Math.floorMod((word + "#" + i + salt).hashCode(), 11) - 5;
            result.add(new PhonemeScoreDto(group, round2(clamp(wordScore + delta))));
        }
        return result;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(100, v));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
