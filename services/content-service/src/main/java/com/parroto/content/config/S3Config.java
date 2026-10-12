package com.parroto.content.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client(@Value("${aws.s3.region}") String region,
                              @Value("${aws.s3.access-key}") String accessKey,
                              @Value("${aws.s3.secret-key}") String secretKey) {
        AwsCredentialsProvider credentialsProvider = (accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank())
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
                : DefaultCredentialsProvider.create();

        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
