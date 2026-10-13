package com.parroto.content.service.storage;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Storage for lesson/sentence audio. The value returned by {@link #store} is persisted in
 * {@code lesson.audio_url} and later passed back to the read methods. Values are one of:
 * <ul>
 *   <li>{@code local:<path>} – a file under {@code ${MEDIA_ROOT}}</li>
 *   <li>{@code https://<bucket>.s3.<region>.amazonaws.com/<key>} or a bare S3 key</li>
 * </ul>
 */
public interface LessonAudioStorage {

    String LOCAL_PREFIX = "local:";

    /** Stores the file under {@code keyPrefix} (e.g. {@code lessons/<lessonId>}) and returns the audio_url value. */
    String store(MultipartFile file, String keyPrefix);

    /** Streams the audio inline, honoring an HTTP {@code Range} header (206 Partial Content). */
    void stream(String audioUrl, String rangeHeader, HttpServletResponse response);

    /** Sends the whole audio file as an attachment. */
    void download(String audioUrl, HttpServletResponse response);

    /** Best-effort removal of a previously stored file; never fails the caller for missing files. */
    void delete(String audioUrl);

    static boolean isLocal(String audioUrl) {
        return audioUrl != null && audioUrl.startsWith(LOCAL_PREFIX);
    }
}
