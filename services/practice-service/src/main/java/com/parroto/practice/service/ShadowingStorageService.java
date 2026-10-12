package com.parroto.practice.service;

import com.parroto.practice.exception.ApiException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.UUID;

/**
 * Manages shadowing voice recordings in S3: the frontend uploads directly to a
 * presigned PUT URL, and this service later reads the same object back (for Azure
 * pronunciation assessment) and streams it to the client on request.
 */
@Service
public class ShadowingStorageService {

    private static final Duration UPLOAD_URL_TTL = Duration.ofHours(1);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;

    public ShadowingStorageService(S3Client s3Client, S3Presigner s3Presigner,
                                    @Value("${aws.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
    }

    public record PresignedUpload(String uploadUrl, String recordingId, String key) {
    }

    public PresignedUpload createUploadUrl(UUID userId) {
        String recordingId = UUID.randomUUID().toString();
        String key = keyFor(userId, recordingId);
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("audio/wav")
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(UPLOAD_URL_TTL)
                .putObjectRequest(objectRequest)
                .build();
        String url = s3Presigner.presignPutObject(presignRequest).url().toString();
        return new PresignedUpload(url, recordingId, key);
    }

    public String keyFor(UUID userId, String recordingId) {
        return "shadowing/%s/%s.wav".formatted(userId, recordingId);
    }

    public byte[] downloadBytes(String key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();
            try (ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(request)) {
                return stream.readAllBytes();
            }
        } catch (NoSuchKeyException e) {
            throw ApiException.notFound("Recording not found — upload it first via request-upload-url");
        } catch (IOException | S3Exception e) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Failed to read recording: " + e.getMessage());
        }
    }

    public void stream(String key, HttpServletResponse response) {
        try {
            response.setContentType("audio/wav");
            GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();
            try (ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(request);
                 OutputStream out = response.getOutputStream()) {
                stream.transferTo(out);
            }
        } catch (NoSuchKeyException e) {
            throw ApiException.notFound("Recording not found");
        } catch (IOException | S3Exception e) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Failed to stream recording: " + e.getMessage());
        }
    }
}
