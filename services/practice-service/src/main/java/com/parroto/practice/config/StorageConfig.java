package com.parroto.practice.config;

import com.parroto.practice.service.storage.LocalRecordingStorage;
import com.parroto.practice.service.storage.RecordingStorage;
import com.parroto.practice.service.storage.S3RecordingStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.nio.file.Path;

/**
 * Creates exactly one {@link RecordingStorage} bean (architecture §8.2). S3 clients are only
 * built when the resolved provider is {@code s3}, so the service boots without AWS keys.
 * Replaces the former always-on {@code S3Config}.
 */
@Configuration
public class StorageConfig {

    @Bean
    public RecordingStorage recordingStorage(@Value("${app.storage.provider:auto}") String provider,
                                             @Value("${app.storage.local-root:/data/media}") String localRoot,
                                             @Value("${aws.s3.access-key:}") String accessKey,
                                             @Value("${aws.s3.secret-key:}") String secretKey,
                                             @Value("${aws.s3.region:us-east-1}") String region,
                                             @Value("${aws.s3.bucket:}") String bucket) {
        String resolved = ProviderSelection.resolveStorage(provider, accessKey, secretKey);
        if (ProviderSelection.STORAGE_S3.equals(resolved)) {
            if (bucket == null || bucket.isBlank()) {
                throw new IllegalStateException("Storage provider s3 requires AWS_S3_BUCKET");
            }
            StaticCredentialsProvider credentials =
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey.trim(), secretKey.trim()));
            S3Client s3Client = S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(credentials)
                    .build();
            S3Presigner presigner = S3Presigner.builder()
                    .region(Region.of(region))
                    .credentialsProvider(credentials)
                    .build();
            return new S3RecordingStorage(s3Client, presigner, bucket);
        }
        return new LocalRecordingStorage(Path.of(localRoot));
    }
}
