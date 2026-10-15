package com.parroto.practice.util;

import com.parroto.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WavUtilsTest {

    @Test
    void acceptsPcm16kMono16bit() {
        byte[] wav = WavFixtures.toneWav(2.0);
        WavUtils.WavInfo info = WavUtils.validate(wav);
        assertThat(info.sampleRate()).isEqualTo(16000);
        assertThat(info.channels()).isEqualTo(1);
        assertThat(info.bitsPerSample()).isEqualTo(16);
        assertThat(info.dataOffset()).isEqualTo(44);
        assertThat(info.durationSeconds()).isEqualTo(2.0);
        assertThat(WavUtils.samples(wav, info)).hasSize(32000);
    }

    @Test
    void rejectsWebmAndGarbage() {
        byte[] webm = new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        assertBadFormat(webm);
        assertBadFormat("hello world, not audio".getBytes(StandardCharsets.UTF_8));
        assertBadFormat(new byte[0]);
        assertBadFormat(null);
        assertThat(WavUtils.hasWavHeader(webm)).isFalse();
    }

    @Test
    void rejectsWrongSampleRateOrChannels() {
        assertBadFormat(WavUtils.encodePcm16(WavFixtures.tone(2.0, 48000, 0.5), 48000, 1));
        assertBadFormat(WavUtils.encodePcm16(WavFixtures.tone(2.0, 16000, 0.5), 16000, 2));
    }

    @Test
    void rejectsNonPcmFormat() {
        byte[] wav = WavFixtures.toneWav(1.0);
        ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN).putShort(20, (short) 3); // IEEE float
        assertBadFormat(wav);
    }

    @Test
    void rejectsTooShortOrTooLong() {
        assertThatThrownBy(() -> WavUtils.validate(WavFixtures.toneWav(0.3)))
                .isInstanceOf(ApiException.class)
                .hasMessage(WavUtils.INVALID_DURATION_MESSAGE);
        assertThatThrownBy(() -> WavUtils.validate(WavFixtures.toneWav(31)))
                .isInstanceOf(ApiException.class)
                .hasMessage(WavUtils.INVALID_DURATION_MESSAGE);
        WavUtils.validate(WavFixtures.toneWav(0.5));
        WavUtils.validate(WavFixtures.toneWav(30));
    }

    @Test
    void skipsExtraChunksBeforeData() {
        byte[] plain = WavFixtures.toneWav(1.0);
        // Insert a 6-byte LIST chunk between "fmt " and "data".
        byte[] list = new byte[14];
        ByteBuffer.wrap(list).order(ByteOrder.LITTLE_ENDIAN)
                .put("LIST".getBytes(StandardCharsets.US_ASCII)).putInt(6).put(new byte[6]);
        byte[] wav = new byte[plain.length + list.length];
        System.arraycopy(plain, 0, wav, 0, 36);
        System.arraycopy(list, 0, wav, 36, list.length);
        System.arraycopy(plain, 36, wav, 36 + list.length, plain.length - 36);

        WavUtils.WavInfo info = WavUtils.validate(wav);
        assertThat(info.dataOffset()).isEqualTo(44 + list.length);
        assertThat(info.durationSeconds()).isEqualTo(1.0);
    }

    @Test
    void toleratesStreamingDataSizePlaceholder() {
        byte[] wav = WavFixtures.toneWav(1.0);
        ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN).putInt(40, 0xFFFFFFFF);
        assertThat(WavUtils.validate(wav).durationSeconds()).isEqualTo(1.0);
    }

    private static void assertBadFormat(byte[] bytes) {
        assertThatThrownBy(() -> WavUtils.validate(bytes))
                .isInstanceOf(ApiException.class)
                .hasMessage(WavUtils.INVALID_FORMAT_MESSAGE)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
