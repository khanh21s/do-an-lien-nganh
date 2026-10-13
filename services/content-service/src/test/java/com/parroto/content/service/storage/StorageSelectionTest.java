package com.parroto.content.service.storage;

import com.parroto.content.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Provider resolution (architecture §8.1), upload validation (ADM-04) and read routing (§8.3). */
class StorageSelectionTest {

    @Test
    void autoFallsBackToLocalWithoutRealKeys() {
        assertThat(StorageProvider.resolve("auto", "", "")).isEqualTo(StorageProvider.LOCAL);
        assertThat(StorageProvider.resolve(null, null, null)).isEqualTo(StorageProvider.LOCAL);
        assertThat(StorageProvider.resolve("auto", "AKIAXXX", "")).isEqualTo(StorageProvider.LOCAL);
        assertThat(StorageProvider.resolve("auto", "your_aws_access_key", "your_secret"))
                .isEqualTo(StorageProvider.LOCAL);
        assertThat(StorageProvider.resolve("AUTO", "AKIAXXX", "secret")).isEqualTo(StorageProvider.S3);
        assertThat(StorageProvider.resolve("local", "AKIAXXX", "secret")).isEqualTo(StorageProvider.LOCAL);
    }

    @Test
    void explicitS3WithoutKeysOrUnknownProviderFailsFast() {
        assertThatThrownBy(() -> StorageProvider.resolve("s3", "", ""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> StorageProvider.resolve("minio", "a", "b"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsAudioUploads() {
        assertThat(AudioFormats.validateUpload(new MockMultipartFile("file", "a.mp3", "audio/mpeg", new byte[]{1})))
                .isEqualTo("mp3");
        assertThat(AudioFormats.validateUpload(new MockMultipartFile("file", "b.WEBM", "audio/webm", new byte[]{1})))
                .isEqualTo("webm");
    }

    @Test
    void rejectsNonAudioUploads() {
        assertBadRequest(new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[]{1}));
        assertBadRequest(new MockMultipartFile("file", "virus.exe", "audio/mpeg", new byte[]{1}));
        assertBadRequest(new MockMultipartFile("file", "a.mp3", "application/octet-stream", new byte[]{1}));
        assertBadRequest(new MockMultipartFile("file", "a.mp3", null, new byte[]{1}));
        assertBadRequest(new MockMultipartFile("file", "a.mp3", "audio/mpeg", new byte[0]));
    }

    @Test
    void contentTypeFromExtension() {
        assertThat(AudioFormats.contentTypeFor("lessons/a/x.mp3")).isEqualTo("audio/mpeg");
        assertThat(AudioFormats.contentTypeFor("x.m4a")).isEqualTo("audio/mp4");
        assertThat(AudioFormats.contentTypeFor("x.wav")).isEqualTo("audio/wav");
        assertThat(AudioFormats.contentTypeFor("x.ogg")).isEqualTo("audio/ogg");
        assertThat(AudioFormats.contentTypeFor("x.webm")).isEqualTo("audio/webm");
        assertThat(AudioFormats.contentTypeFor("legacy-key-without-ext")).isEqualTo("audio/mpeg");
    }

    @Test
    void localModeReturns404ForS3OrMissingAudio(@org.junit.jupiter.api.io.TempDir Path root) {
        RoutingLessonAudioStorage routing = new RoutingLessonAudioStorage(
                new LocalLessonAudioStorage(root.toString()), null);
        assertThat(routing.provider()).isEqualTo(StorageProvider.LOCAL);

        for (String url : new String[]{"https://b.s3.us-east-1.amazonaws.com/lessons/x.mp3", "lessons/x.mp3", null, " "}) {
            assertThatThrownBy(() -> routing.stream(url, null, new MockHttpServletResponse()))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
        routing.delete("https://b.s3.us-east-1.amazonaws.com/lessons/x.mp3"); // no-op, no exception
    }

    private static void assertBadRequest(MockMultipartFile file) {
        assertThatThrownBy(() -> AudioFormats.validateUpload(file))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
