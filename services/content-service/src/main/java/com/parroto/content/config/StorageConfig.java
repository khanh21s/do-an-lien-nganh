package com.parroto.content.config;

import com.parroto.content.service.storage.LessonAudioStorage;
import com.parroto.content.service.storage.LocalLessonAudioStorage;
import com.parroto.content.service.storage.RoutingLessonAudioStorage;
import com.parroto.content.service.storage.S3LessonAudioStorage;
import com.parroto.content.service.storage.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Creates the single {@link LessonAudioStorage} bean. The S3 client is only built when the provider
 * resolves to {@code s3}; without AWS keys the service runs entirely on local storage (MEDIA_ROOT).
 */
@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    public LessonAudioStorage lessonAudioStorage(
            @Value("${app.storage.provider:auto}") String configuredProvider,
            @Value("${app.storage.local-root:/data/media}") String localRoot,
            @Value("${aws.s3.bucket:}") String bucket,
            @Value("${aws.s3.region:us-east-1}") String region,
            @Value("${aws.s3.access-key:}") String accessKey,
            @Value("${aws.s3.secret-key:}") String secretKey) {
        StorageProvider provider = StorageProvider.resolve(configuredProvider, accessKey, secretKey);
        LocalLessonAudioStorage local = new LocalLessonAudioStorage(localRoot);
        S3LessonAudioStorage s3 = null;
        if (provider == StorageProvider.S3) {
            if (bucket == null || bucket.isBlank()) {
                throw new IllegalStateException("Storage provider s3 requires AWS_S3_BUCKET");
            }
            S3Client client = S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey.trim(), secretKey.trim())))
                    .build();
            s3 = new S3LessonAudioStorage(client, bucket, region);
            log.info("Storage provider: s3 (bucket={}, region={}) | local reads from MEDIA_ROOT={}",
                    bucket, region, local.root());
        } else {
            log.info("Storage provider: local (MEDIA_ROOT={})", local.root());
        }
        return new RoutingLessonAudioStorage(local, s3);
    }
}
