package com.parroto.content.service.storage;

import com.parroto.content.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalLessonAudioStorageTest {

    private static final byte[] DATA = "0123456789".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path root;

    private LocalLessonAudioStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalLessonAudioStorage(root.toString());
    }

    @Test
    void storeWritesFileUnderLessonsAndReturnsLocalUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "Talk.MP3", "audio/mpeg", DATA);

        String url = storage.store(file, "lessons/abc");

        assertThat(url).startsWith("local:lessons/abc/").endsWith(".mp3");
        Path written = root.resolve(url.substring("local:".length()));
        assertThat(Files.readAllBytes(written)).isEqualTo(DATA);
        try (var files = Files.list(written.getParent())) {
            assertThat(files).hasSize(1); // no leftover temp file
        }
    }

    @Test
    void streamWholeFileWithContentTypeFromExtension() throws Exception {
        seed("seed/lesson-1/full.mp3");
        MockHttpServletResponse res = new MockHttpServletResponse();

        storage.stream("local:seed/lesson-1/full.mp3", null, res);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentType()).isEqualTo("audio/mpeg");
        assertThat(res.getHeader("Accept-Ranges")).isEqualTo("bytes");
        assertThat(res.getContentLengthLong()).isEqualTo(10);
        assertThat(res.getContentAsByteArray()).isEqualTo(DATA);
    }

    @Test
    void streamHonorsRangeWith206() throws Exception {
        seed("lessons/x/a.wav");
        MockHttpServletResponse res = new MockHttpServletResponse();

        storage.stream("local:lessons/x/a.wav", "bytes=2-5", res);

        assertThat(res.getStatus()).isEqualTo(206);
        assertThat(res.getContentType()).isEqualTo("audio/wav");
        assertThat(res.getHeader("Content-Range")).isEqualTo("bytes 2-5/10");
        assertThat(res.getContentLengthLong()).isEqualTo(4);
        assertThat(res.getContentAsString()).isEqualTo("2345");
    }

    @Test
    void streamHonorsOpenEndedAndSuffixRanges() throws Exception {
        seed("lessons/x/a.ogg");

        MockHttpServletResponse open = new MockHttpServletResponse();
        storage.stream("local:lessons/x/a.ogg", "bytes=7-", open);
        assertThat(open.getStatus()).isEqualTo(206);
        assertThat(open.getContentAsString()).isEqualTo("789");
        assertThat(open.getHeader("Content-Range")).isEqualTo("bytes 7-9/10");

        MockHttpServletResponse suffix = new MockHttpServletResponse();
        storage.stream("local:lessons/x/a.ogg", "bytes=-3", suffix);
        assertThat(suffix.getContentAsString()).isEqualTo("789");

        MockHttpServletResponse clamped = new MockHttpServletResponse();
        storage.stream("local:lessons/x/a.ogg", "bytes=8-100", clamped);
        assertThat(clamped.getContentAsString()).isEqualTo("89");
        assertThat(clamped.getHeader("Content-Range")).isEqualTo("bytes 8-9/10");
    }

    @Test
    void unsatisfiableRangeReturns416() throws Exception {
        seed("lessons/x/a.mp3");
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertThatThrownBy(() -> storage.stream("local:lessons/x/a.mp3", "bytes=10-", res))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        assertThat(res.getHeader("Content-Range")).isEqualTo("bytes */10");
    }

    @Test
    void malformedRangeServesFullBody() throws Exception {
        seed("lessons/x/a.mp3");
        MockHttpServletResponse res = new MockHttpServletResponse();

        storage.stream("local:lessons/x/a.mp3", "bytes=abc", res);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentAsByteArray()).isEqualTo(DATA);
    }

    @Test
    void blocksPathTraversal() throws Exception {
        Files.writeString(root.getParent().resolve("secret.mp3"), "secret");

        for (String url : new String[]{"local:../secret.mp3", "local:seed/../../secret.mp3",
                "local:/etc/passwd", "local:seed\\..\\x.mp3", "local:"}) {
            assertThatThrownBy(() -> storage.stream(url, null, new MockHttpServletResponse()))
                    .as(url)
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThatThrownBy(() -> storage.store(
                new MockMultipartFile("file", "a.mp3", "audio/mpeg", DATA), "../outside"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void missingFileIs404() {
        assertThatThrownBy(() -> storage.stream("local:lessons/none.mp3", null, new MockHttpServletResponse()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void downloadSendsAttachment() throws Exception {
        seed("lessons/x/a.m4a");
        MockHttpServletResponse res = new MockHttpServletResponse();

        storage.download("local:lessons/x/a.m4a", res);

        assertThat(res.getContentType()).isEqualTo("audio/mp4");
        assertThat(res.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"a.m4a\"");
        assertThat(res.getContentAsByteArray()).isEqualTo(DATA);
    }

    @Test
    void deleteRemovesUploadedFilesButNeverSeedFiles() throws Exception {
        Path uploaded = seed("lessons/x/a.mp3");
        Path seedFile = seed("seed/lesson-1/full.mp3");

        storage.delete("local:lessons/x/a.mp3");
        storage.delete("local:seed/lesson-1/full.mp3");
        storage.delete("local:lessons/../seed/lesson-1/full.mp3");
        storage.delete("https://bucket.s3.us-east-1.amazonaws.com/k.mp3");

        assertThat(uploaded).doesNotExist();
        assertThat(seedFile).exists();
    }

    private Path seed(String relative) throws Exception {
        Path p = root.resolve(relative);
        Files.createDirectories(p.getParent());
        Files.write(p, DATA);
        return p;
    }
}
