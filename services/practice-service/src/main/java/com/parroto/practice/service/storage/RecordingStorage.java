package com.parroto.practice.service.storage;

import com.parroto.practice.exception.ApiException;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Locale;
import java.util.UUID;

/**
 * Where shadowing recordings live (architecture §8.2). Exactly one implementation is
 * active, chosen by {@code app.storage.provider} in {@code StorageConfig}.
 * Both implementations use the same storage key layout: {@code shadowing/<userId>/<recordingId>.wav}.
 */
public interface RecordingStorage {

    String CONTENT_TYPE = "audio/wav";
    long UPLOAD_TTL_SECONDS = 3600;

    /**
     * @param uploadUrl    presigned S3 URL (s3) or gateway-relative path (local)
     * @param requiresAuth whether the client must send its Bearer token with the PUT
     * @param storage      "s3" | "local"
     */
    record UploadTarget(String uploadUrl, String recordingId, String key, long expiresIn,
                        boolean requiresAuth, String storage) {
    }

    /** "s3" or "local". */
    String provider();

    /** Human-readable description for the startup log line. */
    String describe();

    UploadTarget createUpload(UUID userId);

    /** Local mode only: stores the uploaded WAV under the user's own key. S3 mode → 404. */
    void putLocal(UUID userId, String recordingId, byte[] wav);

    /** @throws ApiException 404 when the recording does not exist */
    byte[] read(String key);

    /** Writes the recording to the response with {@code Content-Type: audio/wav}. */
    void stream(String key, HttpServletResponse response);

    static String keyFor(UUID userId, String recordingId) {
        return "shadowing/%s/%s.wav".formatted(userId, recordingId);
    }

    /**
     * recordingId is client-supplied and ends up in a storage key / file path, so it must be a UUID
     * (blocks path traversal). Returns the canonical lower-case form.
     */
    static String requireValidRecordingId(String recordingId) {
        if (recordingId == null) {
            throw ApiException.badRequest("recordingId không hợp lệ");
        }
        try {
            String canonical = UUID.fromString(recordingId.trim()).toString();
            if (!canonical.equals(recordingId.trim().toLowerCase(Locale.ROOT))) {
                throw ApiException.badRequest("recordingId không hợp lệ");
            }
            return canonical;
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("recordingId không hợp lệ");
        }
    }
}
