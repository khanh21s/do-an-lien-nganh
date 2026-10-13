package com.parroto.content.service.storage;

import com.parroto.content.exception.ApiException;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/** Lesson audio stored in AWS S3; only instantiated when the storage provider resolves to {@code s3}. */
public class S3LessonAudioStorage implements LessonAudioStorage {

    private static final Logger log = LoggerFactory.getLogger(S3LessonAudioStorage.class);

    private final S3Client s3Client;
    private final String bucket;
    private final String region;

    public S3LessonAudioStorage(S3Client s3Client, String bucket, String region) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.region = region;
    }

    public String publicUrl(String key) {
        return "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, region, key);
    }

    @Override
    public String store(MultipartFile file, String keyPrefix) {
        String ext = AudioFormats.extensionOf(file.getOriginalFilename());
        if (!AudioFormats.isAllowedExtension(ext)) {
            ext = "mp3";
        }
        String key = "%s/%s.%s".formatted(keyPrefix, UUID.randomUUID(), ext);
        try (InputStream in = file.getInputStream()) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(AudioFormats.contentTypeFor(key))
                    .cacheControl("public, max-age=86400")
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(in, file.getSize()));
        } catch (IOException | S3Exception e) {
            log.error("S3 upload failed for key {}: {}", key, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Không tải được audio lên S3");
        }
        return publicUrl(key);
    }

    @Override
    public void stream(String audioUrl, String rangeHeader, HttpServletResponse response) {
        String key = toKey(audioUrl);
        try {
            long total = s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build())
                    .contentLength();
            ByteRange range = ByteRange.parse(rangeHeader, total, response);

            GetObjectRequest.Builder get = GetObjectRequest.builder().bucket(bucket).key(key);
            response.setContentType(AudioFormats.contentTypeFor(key));
            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader("Cache-Control", "public, max-age=86400");
            if (range != null) {
                get.range("bytes=%d-%d".formatted(range.start(), range.end()));
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setHeader("Content-Range", range.contentRange(total));
                response.setContentLengthLong(range.length());
            } else {
                response.setContentLengthLong(total);
            }
            transfer(get.build(), response);
        } catch (NoSuchKeyException e) {
            throw ApiException.notFound("Audio file not found");
        } catch (S3Exception e) {
            throw s3Error(e, "stream");
        }
    }

    @Override
    public void download(String audioUrl, HttpServletResponse response) {
        String key = toKey(audioUrl);
        try {
            response.setContentType(AudioFormats.contentTypeFor(key));
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + key.substring(key.lastIndexOf('/') + 1) + "\"");
            transfer(GetObjectRequest.builder().bucket(bucket).key(key).build(), response);
        } catch (NoSuchKeyException e) {
            throw ApiException.notFound("Audio file not found");
        } catch (S3Exception e) {
            throw s3Error(e, "download");
        }
    }

    @Override
    public void delete(String audioUrl) {
        if (audioUrl == null || audioUrl.isBlank() || LessonAudioStorage.isLocal(audioUrl)) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(toKey(audioUrl)).build());
        } catch (S3Exception e) {
            log.warn("Could not delete old S3 audio {}: {}", audioUrl, e.getMessage());
        }
    }

    private void transfer(GetObjectRequest request, HttpServletResponse response) {
        try (ResponseInputStream<GetObjectResponse> in = s3Client.getObject(request)) {
            OutputStream out = response.getOutputStream();
            in.transferTo(out);
            out.flush();
        } catch (IOException e) {
            if (response.isCommitted()) {
                log.debug("S3 audio stream aborted: {}", e.getMessage());
                return;
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Không đọc được audio từ S3");
        }
    }

    private static ApiException s3Error(S3Exception e, String action) {
        if (e.statusCode() == 404) {
            return ApiException.notFound("Audio file not found");
        }
        log.error("S3 {} failed: {}", action, e.getMessage());
        return new ApiException(HttpStatus.BAD_GATEWAY, "Không đọc được audio từ S3");
    }

    private String toKey(String audioUrl) {
        String prefix = publicUrl("");
        return audioUrl.startsWith(prefix) ? audioUrl.substring(prefix.length()) : audioUrl;
    }
}
