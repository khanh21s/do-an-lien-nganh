package com.parroto.practice.util;

/** Synthetic WAV payloads for tests. */
public final class WavFixtures {

    private WavFixtures() {
    }

    public static short[] tone(double seconds, int sampleRate, double amplitude) {
        int n = (int) Math.round(seconds * sampleRate);
        short[] samples = new short[n];
        for (int i = 0; i < n; i++) {
            samples[i] = (short) Math.round(amplitude * 32767 * Math.sin(2 * Math.PI * 220 * i / sampleRate));
        }
        return samples;
    }

    public static byte[] toneWav(double seconds) {
        return WavUtils.encodePcm16(tone(seconds, 16000, 0.5), 16000, 1);
    }

    public static byte[] silentWav(double seconds) {
        return WavUtils.encodePcm16(new short[(int) Math.round(seconds * 16000)], 16000, 1);
    }

    /** First half voiced, second half silent. */
    public static byte[] halfVoicedWav(double seconds) {
        short[] samples = tone(seconds, 16000, 0.5);
        for (int i = samples.length / 2; i < samples.length; i++) {
            samples[i] = 0;
        }
        return WavUtils.encodePcm16(samples, 16000, 1);
    }
}
