package com.parroto.practice.dto;

import com.parroto.practice.service.storage.RecordingStorage;

/**
 * @param uploadUrl          presigned S3 URL (storage=s3) or gateway-relative path
 *                           {@code /practice/shadowing/recordings/{recordingId}} (storage=local)
 * @param uploadMethod       always "PUT"
 * @param uploadRequiresAuth true: send the Bearer token with the PUT (local); false: plain PUT to S3
 * @param contentType        always "audio/wav" (part of the S3 signature)
 * @param storage            "s3" | "local"
 */
public record ShadowingUploadUrlResponse(
        String uploadUrl,
        String recordingId,
        long expiresIn,
        String uploadMethod,
        boolean uploadRequiresAuth,
        String contentType,
        String storage
) {
    public static ShadowingUploadUrlResponse from(RecordingStorage.UploadTarget target) {
        return new ShadowingUploadUrlResponse(target.uploadUrl(), target.recordingId(), target.expiresIn(),
                "PUT", target.requiresAuth(), RecordingStorage.CONTENT_TYPE, target.storage());
    }
}
