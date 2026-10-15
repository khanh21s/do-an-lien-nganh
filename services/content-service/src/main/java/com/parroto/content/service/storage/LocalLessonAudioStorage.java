package com.parroto.content.service.storage;

import com.parroto.common.exception.ApiException;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * Stores lesson audio on the local filesystem under {@code ${MEDIA_ROOT}}; used whenever no AWS
 * credentials are configured (architecture §8.3). Stored values look like
 * {@code local:lessons/<lessonId>/<uuid>.<ext>}; seed data uses {@code local:seed/...}.
 */
public class LocalLessonAudioStorage implements LessonAudioStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalLessonAudioStorage.class);
    static final String NOT_AVAILABLE = "Audio chưa khả dụng";
    /** Only files written by this service may be deleted (seed files are mounted read-only). */
    private static final String DELETABLE_PREFIX = "lessons/";

    private final Path root;

    public LocalLessonAudioStorage(String localRoot) {
        this.root = Paths.get(localRoot).toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    @Override
    public String store(MultipartFile file, String keyPrefix) {
        String ext = AudioFormats.extensionOf(file.getOriginalFilename());
        if (!AudioFormats.isAllowedExtension(ext)) {
            ext = "mp3";
        }
        String relative = "%s/%s.%s".formatted(trimSlashes(keyPrefix), UUID.randomUUID(), ext);
        Path target = resolveRelative(relative);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), ".upload-", ".part");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            log.error("Failed to store audio at {}: {}", target, e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không lưu được file audio");
        }
        return LOCAL_PREFIX + relative;
    }

    @Override
    public void stream(String audioUrl, String rangeHeader, HttpServletResponse response) {
        Path file = resolveExisting(audioUrl);
        long total = size(file);
        ByteRange range = ByteRange.parse(rangeHeader, total, response);

        response.setContentType(AudioFormats.contentTypeFor(file.getFileName().toString()));
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Cache-Control", "public, max-age=86400");
        long start = 0;
        long length = total;
        if (range != null) {
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Content-Range", range.contentRange(total));
            start = range.start();
            length = range.length();
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
        }
        response.setContentLengthLong(length);
        copy(file, start, length, response);
    }

    @Override
    public void download(String audioUrl, HttpServletResponse response) {
        Path file = resolveExisting(audioUrl);
        long total = size(file);
        response.setContentType(AudioFormats.contentTypeFor(file.getFileName().toString()));
        response.setHeader("Content-Disposition", "attachment; filename=\"" + file.getFileName() + "\"");
        response.setContentLengthLong(total);
        copy(file, 0, total, response);
    }

    @Override
    public void delete(String audioUrl) {
        if (!LessonAudioStorage.isLocal(audioUrl)) {
            return;
        }
        String relative = audioUrl.substring(LOCAL_PREFIX.length());
        if (!relative.startsWith(DELETABLE_PREFIX)) {
            return;
        }
        try {
            Files.deleteIfExists(resolveRelative(relative));
        } catch (ApiException | IOException e) {
            log.warn("Could not delete old audio {}: {}", audioUrl, e.getMessage());
        }
    }

    /** Resolves a {@code local:} value to a path inside the media root; rejects traversal with 404. */
    Path resolve(String audioUrl) {
        if (!LessonAudioStorage.isLocal(audioUrl)) {
            throw ApiException.notFound(NOT_AVAILABLE);
        }
        return resolveRelative(audioUrl.substring(LOCAL_PREFIX.length()));
    }

    private Path resolveRelative(String relative) {
        if (relative == null || relative.isBlank() || relative.startsWith("/") || relative.contains("\\")
                || relative.contains("\0")) {
            throw ApiException.notFound(NOT_AVAILABLE);
        }
        for (String segment : relative.split("/")) {
            if (segment.equals("..") || segment.equals(".")) {
                throw ApiException.notFound(NOT_AVAILABLE);
            }
        }
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw ApiException.notFound(NOT_AVAILABLE);
        }
        return path;
    }

    private Path resolveExisting(String audioUrl) {
        Path path = resolve(audioUrl);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw ApiException.notFound(NOT_AVAILABLE);
        }
        return path;
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw ApiException.notFound(NOT_AVAILABLE);
        }
    }

    private static void copy(Path file, long start, long length, HttpServletResponse response) {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            OutputStream out = response.getOutputStream();
            WritableByteChannel target = Channels.newChannel(out);
            long position = start;
            long remaining = length;
            while (remaining > 0) {
                long sent = channel.transferTo(position, remaining, target);
                if (sent <= 0) {
                    break;
                }
                position += sent;
                remaining -= sent;
            }
            out.flush();
        } catch (IOException e) {
            if (response.isCommitted()) {
                // Typically the player aborted the request while seeking; nothing left to report.
                log.debug("Audio stream aborted for {}: {}", file, e.getMessage());
                return;
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không đọc được file audio");
        }
    }

    private static String trimSlashes(String s) {
        String result = s == null ? "" : s.trim();
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
