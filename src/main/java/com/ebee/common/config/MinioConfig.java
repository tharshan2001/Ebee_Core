package com.ebee.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import java.net.URI;
import java.net.UnknownHostException;

@Configuration
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    private final MinioProperties minioProperties;

    public MinioConfig(MinioProperties minioProperties) {
        this.minioProperties = minioProperties;
    }

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(minioProperties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                ));

        if (minioProperties.getEndpoint() != null && !minioProperties.getEndpoint().isBlank()) {
            builder = builder
                    .endpointOverride(URI.create(minioProperties.getEndpoint()))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build());
        }

        return builder.build();
    }

    @Bean
    boolean ensureBucketExists(S3Client s3Client) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(minioProperties.getBucketName())
                    .build());
            log.info("Bucket '{}' already exists", minioProperties.getBucketName());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                s3Client.createBucket(CreateBucketRequest.builder()
                        .bucket(minioProperties.getBucketName())
                        .build());
                log.info("Created bucket '{}'", minioProperties.getBucketName());
            } else {
                log.warn("Could not check/create bucket '{}': {}", minioProperties.getBucketName(), e.getMessage());
            }
        } catch (RuntimeException e) {
            log.warn("MinIO is not reachable at '{}': {}. File uploads will fail until it's available.",
                    minioProperties.getEndpoint(), e.getMessage());
        }

        applyPublicReadPolicy(s3Client);

        return true;
    }

    private void applyPublicReadPolicy(S3Client s3Client) {
        try {
            var policy = """
                    {
                      "Version": "2012-10-17",
                      "Statement": [
                        {
                          "Effect": "Allow",
                          "Principal": "*",
                          "Action": ["s3:GetObject"],
                          "Resource": ["arn:aws:s3:::%s/*"]
                        }
                      ]
                    }
                    """.formatted(minioProperties.getBucketName());

            s3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
                    .bucket(minioProperties.getBucketName())
                    .policy(policy)
                    .build());
            log.info("Bucket '{}' is now publicly readable", minioProperties.getBucketName());
        } catch (RuntimeException e) {
            log.warn("Could not set public-read policy on bucket '{}': {}", minioProperties.getBucketName(), e.getMessage());
        }
    }
}
