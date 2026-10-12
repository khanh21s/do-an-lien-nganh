package com.parroto.content.service;

import com.parroto.content.exception.ApiException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

@Service
public class S3StorageService {

    private final S3Client s3Client;
    private final String bucket;
    private final String region;

    public S3StorageService(S3Client s3Client,
                             @Value("${aws.s3.bucket}") String bucket,
                             @Value("${aws.s3.region}") String region) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.region = region;
    }

    public String publicUrl(String key) {
        return "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, region, key);
    }

    public String uploadAudio(MultipartFile file, String keyPrefix) {
        String key = "%s/%s.mp3".formatted(keyPrefix, UUID.randomUUID());
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("audio/mpeg")
                    .cacheControl("public, max-age=86400")
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | S3Exception e) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Failed to upload audio: " + e.getMessage());
        }
        return publicUrl(key);
    }

    /**
     * Streams an S3 object to the response, honoring an HTTP Range header for audio seeking.
     * {@code objectKeyOrUrl} may be either a bare S3 key or a full https URL previously
     * returned by {@link #publicUrl}.
     */
    public void stream(String objectKeyOrUrl, String rangeHeader, HttpServletResponse response) {
        String key = toKey(objectKeyOrUrl);
        try {
            HeadObjectRequest head = HeadObjectRequest.builder().bucket(bucket).key(key).build();
            HeadObjectResponse meta = s3Client.headObject(head);
            long contentLength = meta.contentLength();

            GetObjectRequest.Builder getBuilder = GetObjectRequest.builder().bucket(bucket).key(key);

            long start = 0;
            long end = contentLength - 1;
            boolean partial = false;
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String[] parts = rangeHeader.substring(6).split("-");
                start = Long.parseLong(parts[0]);
                if (parts.length > 1 && !parts[1].isBlank()) {
                    end = Long.parseLong(parts[1]);
                }
                getBuilder.range("bytes=%d-%d".formatted(start, end));
                partial = true;
            }

            response.setContentType("audio/mpeg");
            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader("Cache-Control", "public, max-age=86400");

            if (partial) {
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setHeader("Content-Range", "bytes %d-%d/%d".formatted(start, end, contentLength));
                response.setContentLengthLong(end - start + 1);
            } else {
                response.setContentLengthLong(contentLength);
            }

            try (ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getBuilder.build());
                 OutputStream out = response.getOutputStream()) {
                s3Stream.transferTo(out);
            }
        } catch (NoSuchKeyException e) {
            throw ApiException.notFound("Audio file not found");
        } catch (IOException | S3Exception e) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Failed to stream audio: " + e.getMessage());
        }
    }

    public void download(String objectKeyOrUrl, HttpServletResponse response) {
        String key = toKey(objectKeyOrUrl);
        try {
            response.setContentType("audio/mpeg");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + key.substring(key.lastIndexOf('/') + 1) + "\"");
            GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();
            try (ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(request);
                 OutputStream out = response.getOutputStream()) {
                s3Stream.transferTo(out);
            }
        } catch (NoSuchKeyException e) {
            throw ApiException.notFound("Audio file not found");
        } catch (IOException | S3Exception e) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Failed to download audio: " + e.getMessage());
        }
    }

    public void deleteByUrl(String objectKeyOrUrl) {
        if (objectKeyOrUrl == null || objectKeyOrUrl.isBlank()) {
            return;
        }
        String key = toKey(objectKeyOrUrl);
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    private String toKey(String objectKeyOrUrl) {
        String prefix = publicUrl("");
        if (objectKeyOrUrl.startsWith(prefix)) {
            return objectKeyOrUrl.substring(prefix.length());
        }
        return objectKeyOrUrl;
    }
}
