package com.parroto.practice.service.storage;

import com.parroto.practice.exception.ApiException;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Stores recordings on the local filesystem ({@code ${MEDIA_ROOT}/shadowing/<userId>/<recordingId>.wav}),
 * used when no AWS credentials are configured. The client uploads through the gateway with its
 * Bearer token ({@code PUT /practice/shadowing/recordings/{recordingId}}).
 */
public class LocalRecordingStorage implements RecordingStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalRecordingStorage.class);

    private final Path root;

    public LocalRecordingStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    @Override
    public String provider() {
        return "local";
    }

    @Override
    public String describe() {
        return "local (MEDIA_ROOT=" + root + ")";
    }

    @Override
    public UploadTarget createUpload(UUID userId) {
        String recordingId = UUID.randomUUID().toString();
        return new UploadTarget("/practice/shadowing/recordings/" + recordingId, recordingId,
                RecordingStorage.keyFor(userId, recordingId), UPLOAD_TTL_SECONDS, true, provider());
    }

    @Override
    public void putLocal(UUID userId, String recordingId, byte[] wav) {
        String id = RecordingStorage.requireValidRecordingId(recordingId);
        Path target = resolve(RecordingStorage.keyFor(userId, id));
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), id, ".part");
            try {
                Files.write(tmp, wav);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            log.error("Failed to store recording {}: {}", target, e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không lưu được bản ghi");
        }
    }

    @Override
    public byte[] read(String key) {
        Path path = resolve(key);
        try {
            return Files.readAllBytes(path);
        } catch (NoSuchFileException e) {
            throw ApiException.notFound("Không tìm thấy bản ghi – hãy tải bản ghi lên trước");
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không đọc được bản ghi");
        }
    }

    @Override
    public void stream(String key, HttpServletResponse response) {
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) {
            throw ApiException.notFound("Recording not found");
        }
        try {
            response.setContentType(CONTENT_TYPE);
            response.setContentLengthLong(Files.size(path));
            try (OutputStream out = response.getOutputStream()) {
                Files.copy(path, out);
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to stream recording");
        }
    }

    /** Resolves a storage key under the media root, refusing anything that escapes it. */
    Path resolve(String key) {
        if (key == null || key.isBlank()) {
            throw ApiException.notFound("Recording not found");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root.resolve("shadowing"))) {
            throw ApiException.notFound("Recording not found");
        }
        return path;
    }
}
