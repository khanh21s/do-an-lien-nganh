package com.parroto.practice.service.storage;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Presigning is a purely local computation, so this runs offline with dummy credentials. */
class S3RecordingStorageTest {

    @Test
    void presignedPutIsSignedForAudioWav() {
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIATEST", "secret"));
        try (S3Presigner presigner = S3Presigner.builder().region(Region.US_EAST_1).credentialsProvider(credentials).build()) {
            S3RecordingStorage storage = new S3RecordingStorage(mock(S3Client.class), presigner, "bucket");
            UUID userId = UUID.randomUUID();

            RecordingStorage.UploadTarget target = storage.createUpload(userId);

            assertThat(target.storage()).isEqualTo("s3");
            assertThat(target.requiresAuth()).isFalse();
            assertThat(target.key()).isEqualTo("shadowing/" + userId + "/" + target.recordingId() + ".wav");
            assertThat(target.uploadUrl()).startsWith("https://")
                    .contains(target.key())
                    .contains("X-Amz-SignedHeaders=content-type%3Bhost");
        }
    }
}
