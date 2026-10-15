package com.parroto.content.service.storage;

import com.parroto.common.exception.ApiException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * The single {@link LessonAudioStorage} bean. Writes go to the active provider; reads are routed by the
 * <em>value</em> prefix (architecture §8.3): {@code local:} always reads the local file (so seed audio
 * works even with S3 enabled), anything else is read from S3 — or 404 "Audio chưa khả dụng" when S3 is
 * not configured.
 */
public class RoutingLessonAudioStorage implements LessonAudioStorage {

    private final LocalLessonAudioStorage local;
    private final S3LessonAudioStorage s3;
    private final StorageProvider provider;

    /** @param s3 {@code null} when the provider is {@link StorageProvider#LOCAL} */
    public RoutingLessonAudioStorage(LocalLessonAudioStorage local, S3LessonAudioStorage s3) {
        this.local = local;
        this.s3 = s3;
        this.provider = s3 != null ? StorageProvider.S3 : StorageProvider.LOCAL;
    }

    public StorageProvider provider() {
        return provider;
    }

    @Override
    public String store(MultipartFile file, String keyPrefix) {
        return s3 != null ? s3.store(file, keyPrefix) : local.store(file, keyPrefix);
    }

    @Override
    public void stream(String audioUrl, String rangeHeader, HttpServletResponse response) {
        reader(audioUrl).stream(audioUrl, rangeHeader, response);
    }

    @Override
    public void download(String audioUrl, HttpServletResponse response) {
        reader(audioUrl).download(audioUrl, response);
    }

    @Override
    public void delete(String audioUrl) {
        if (audioUrl == null || audioUrl.isBlank()) {
            return;
        }
        if (LessonAudioStorage.isLocal(audioUrl)) {
            local.delete(audioUrl);
        } else if (s3 != null) {
            s3.delete(audioUrl);
        }
    }

    private LessonAudioStorage reader(String audioUrl) {
        if (audioUrl == null || audioUrl.isBlank()) {
            throw ApiException.notFound(LocalLessonAudioStorage.NOT_AVAILABLE);
        }
        if (LessonAudioStorage.isLocal(audioUrl)) {
            return local;
        }
        if (s3 == null) {
            throw ApiException.notFound(LocalLessonAudioStorage.NOT_AVAILABLE);
        }
        return s3;
    }
}
