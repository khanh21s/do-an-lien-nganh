package com.parroto.practice.service.storage;

import com.parroto.practice.exception.ApiException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpStatus;
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
 * Recordings in AWS S3: the browser PUTs straight to a presigned URL (no Bearer), signed
 * with {@code Content-Type: audio/wav} so the client must send exactly that header (SHA-02);
 * the service later reads the object back for scoring and streams it to its owner.
 */
public class S3RecordingStorage implements RecordingStorage, DisposableBean {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;

    public S3RecordingStorage(S3Client s3Client, S3Presigner s3Presigner, String bucket) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
    }

    @Override
    public String provider() {
        return "s3";
    }

    @Override
    public String describe() {
        return "s3 (bucket=" + bucket + ")";
    }

    @Override
    public UploadTarget createUpload(UUID userId) {
        String recordingId = UUID.randomUUID().toString();
        String key = RecordingStorage.keyFor(userId, recordingId);
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(CONTENT_TYPE)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(UPLOAD_TTL_SECONDS))
                .putObjectRequest(objectRequest)
                .build();
        String url = s3Presigner.presignPutObject(presignRequest).url().toString();
        return new UploadTarget(url, recordingId, key, UPLOAD_TTL_SECONDS, false, provider());
    }

    @Override
    public void putLocal(UUID userId, String recordingId, byte[] wav) {
        // Contract: the gateway upload endpoint only exists in local mode.
        throw ApiException.notFound("Not found");
    }

    @Override
    public byte[] read(String key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();
            try (ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(request)) {
                return stream.readAllBytes();
            }
        } catch (NoSuchKeyException e) {
            throw ApiException.notFound("Không tìm thấy bản ghi – hãy tải bản ghi lên trước");
        } catch (IOException | S3Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Failed to read recording: " + e.getMessage());
        }
    }

    @Override
    public void stream(String key, HttpServletResponse response) {
        try {
            GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();
            try (ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(request)) {
                response.setContentType(CONTENT_TYPE);
                Long length = stream.response().contentLength();
                if (length != null) {
                    response.setContentLengthLong(length);
                }
                try (OutputStream out = response.getOutputStream()) {
                    stream.transferTo(out);
                }
            }
        } catch (NoSuchKeyException e) {
            throw ApiException.notFound("Recording not found");
        } catch (IOException | S3Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Failed to stream recording: " + e.getMessage());
        }
    }

    @Override
    public void destroy() {
        s3Presigner.close();
        s3Client.close();
    }
}
