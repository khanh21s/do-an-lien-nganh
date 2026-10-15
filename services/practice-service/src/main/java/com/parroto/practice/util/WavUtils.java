package com.parroto.practice.util;

import com.parroto.common.exception.ApiException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Minimal RIFF/WAVE parser used to validate shadowing recordings before they are stored
 * or scored. Both Azure (short-audio REST) and the mock scorer require
 * PCM, 16 kHz, mono, 16-bit, 0.5–30 s.
 */
public final class WavUtils {

    public static final String INVALID_FORMAT_MESSAGE = "Định dạng ghi âm không hợp lệ (cần WAV PCM 16 kHz mono)";
    public static final String INVALID_DURATION_MESSAGE = "Bản ghi phải dài từ 0,5 đến 30 giây";

    public static final int SAMPLE_RATE = 16000;
    public static final int CHANNELS = 1;
    public static final int BITS_PER_SAMPLE = 16;
    public static final double MIN_DURATION_SECONDS = 0.5;
    public static final double MAX_DURATION_SECONDS = 30.0;

    private static final int FORMAT_PCM = 1;
    private static final int FORMAT_EXTENSIBLE = 0xFFFE;

    private WavUtils() {
    }

    /**
     * @param dataOffset byte offset of the first PCM sample inside the file
     * @param dataLength number of PCM bytes actually present (clamped to the file size)
     */
    public record WavInfo(int audioFormat, int channels, int sampleRate, int bitsPerSample,
                          int dataOffset, int dataLength) {
        public double durationSeconds() {
            int bytesPerSecond = sampleRate * channels * (bitsPerSample / 8);
            return bytesPerSecond == 0 ? 0.0 : (double) dataLength / bytesPerSecond;
        }

        public int sampleCount() {
            int bytesPerFrame = channels * (bitsPerSample / 8);
            return bytesPerFrame == 0 ? 0 : dataLength / bytesPerFrame;
        }
    }

    /** True when the payload starts with a RIFF....WAVE header (cheap pre-check, no full parse). */
    public static boolean hasWavHeader(byte[] bytes) {
        return bytes != null && bytes.length >= 12
                && "RIFF".equals(ascii(bytes, 0))
                && "WAVE".equals(ascii(bytes, 8));
    }

    /**
     * Parses the header without enforcing the 16 kHz/mono/16-bit/duration rules.
     *
     * @throws ApiException 400 when the payload is not a well-formed RIFF/WAVE file
     */
    public static WavInfo parse(byte[] bytes) {
        if (!hasWavHeader(bytes)) {
            throw ApiException.badRequest(INVALID_FORMAT_MESSAGE);
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int pos = 12;
        Integer audioFormat = null;
        int channels = 0;
        int sampleRate = 0;
        int bitsPerSample = 0;
        while (pos + 8 <= bytes.length) {
            String chunkId = ascii(bytes, pos);
            long chunkSize = Integer.toUnsignedLong(buf.getInt(pos + 4));
            int body = pos + 8;
            if ("fmt ".equals(chunkId)) {
                if (chunkSize < 16 || body + 16 > bytes.length) {
                    throw ApiException.badRequest(INVALID_FORMAT_MESSAGE);
                }
                int format = Short.toUnsignedInt(buf.getShort(body));
                channels = Short.toUnsignedInt(buf.getShort(body + 2));
                sampleRate = buf.getInt(body + 4);
                bitsPerSample = Short.toUnsignedInt(buf.getShort(body + 14));
                if (format == FORMAT_EXTENSIBLE && chunkSize >= 40 && body + 26 <= bytes.length) {
                    // WAVEFORMATEXTENSIBLE: the first two bytes of the SubFormat GUID hold the real format tag.
                    format = Short.toUnsignedInt(buf.getShort(body + 24));
                }
                audioFormat = format;
            } else if ("data".equals(chunkId)) {
                if (audioFormat == null) {
                    throw ApiException.badRequest(INVALID_FORMAT_MESSAGE);
                }
                // Streaming recorders sometimes leave the size as 0 or 0xFFFFFFFF: trust the bytes present.
                long available = bytes.length - body;
                long length = (chunkSize == 0 || chunkSize > available) ? available : chunkSize;
                return new WavInfo(audioFormat, channels, sampleRate, bitsPerSample, body, (int) length);
            }
            long next = body + chunkSize + (chunkSize & 1); // chunks are word-aligned
            if (next > bytes.length || next <= pos) {
                break;
            }
            pos = (int) next;
        }
        throw ApiException.badRequest(INVALID_FORMAT_MESSAGE);
    }

    /**
     * Full validation for shadowing recordings: PCM, 16000 Hz, mono, 16-bit, 0.5–30 s.
     *
     * @throws ApiException 400 with a Vietnamese message otherwise
     */
    public static WavInfo validate(byte[] bytes) {
        WavInfo info = parse(bytes);
        if (info.audioFormat() != FORMAT_PCM
                || info.channels() != CHANNELS
                || info.sampleRate() != SAMPLE_RATE
                || info.bitsPerSample() != BITS_PER_SAMPLE) {
            throw ApiException.badRequest(INVALID_FORMAT_MESSAGE);
        }
        double duration = info.durationSeconds();
        if (duration < MIN_DURATION_SECONDS || duration > MAX_DURATION_SECONDS) {
            throw ApiException.badRequest(INVALID_DURATION_MESSAGE);
        }
        return info;
    }

    /** Decodes the 16-bit little-endian mono samples of an already-validated file. */
    public static short[] samples(byte[] bytes, WavInfo info) {
        int count = info.sampleCount();
        short[] out = new short[count];
        ByteBuffer buf = ByteBuffer.wrap(bytes, info.dataOffset(), count * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) {
            out[i] = buf.getShort();
        }
        return out;
    }

    /** Builds a canonical 44-byte-header PCM WAV file (used by tests and tooling). */
    public static byte[] encodePcm16(short[] samples, int sampleRate, int channels) {
        int dataLength = samples.length * 2;
        ByteBuffer buf = ByteBuffer.allocate(44 + dataLength).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(36 + dataLength);
        buf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buf.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(16);
        buf.putShort((short) FORMAT_PCM);
        buf.putShort((short) channels);
        buf.putInt(sampleRate);
        buf.putInt(sampleRate * channels * 2);
        buf.putShort((short) (channels * 2));
        buf.putShort((short) 16);
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(dataLength);
        for (short s : samples) {
            buf.putShort(s);
        }
        return buf.array();
    }

    private static String ascii(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }
}
