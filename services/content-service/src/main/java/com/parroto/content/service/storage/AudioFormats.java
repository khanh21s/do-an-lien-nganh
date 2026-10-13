package com.parroto.content.service.storage;

import com.parroto.content.exception.ApiException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Map;

/** Allowed audio extensions and their Content-Type (architecture §8.3, ADM-04). */
public final class AudioFormats {

    public static final String INVALID_AUDIO_MESSAGE = "File phải là audio (mp3, m4a, wav, ogg, webm)";
    public static final String DEFAULT_CONTENT_TYPE = "audio/mpeg";

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "mp3", "audio/mpeg",
            "m4a", "audio/mp4",
            "wav", "audio/wav",
            "ogg", "audio/ogg",
            "webm", "audio/webm");

    private AudioFormats() {
    }

    /** Lower-case extension without the dot, or "" when there is none. */
    public static String extensionOf(String filenameOrKey) {
        if (filenameOrKey == null) {
            return "";
        }
        String name = filenameOrKey;
        int query = name.indexOf('?');
        if (query >= 0) {
            name = name.substring(0, query);
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isAllowedExtension(String ext) {
        return ext != null && CONTENT_TYPES.containsKey(ext);
    }

    /** Content-Type inferred from the extension; unknown → audio/mpeg (legacy uploads were mp3 only). */
    public static String contentTypeFor(String filenameOrKey) {
        return CONTENT_TYPES.getOrDefault(extensionOf(filenameOrKey), DEFAULT_CONTENT_TYPE);
    }

    /**
     * Validates an uploaded file: non-empty, Content-Type {@code audio/*} and an allowed extension.
     *
     * @return the normalized extension (e.g. "mp3")
     */
    public static String validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("File audio trống");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("audio/")) {
            throw ApiException.badRequest(INVALID_AUDIO_MESSAGE);
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (!isAllowedExtension(ext)) {
            throw ApiException.badRequest(INVALID_AUDIO_MESSAGE);
        }
        return ext;
    }
}
