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
public class S3Config {

    private static final Logger log = LoggerFactory.getLogger(S3Config.class);

    private final AwsProperties awsProperties;

    public S3Config(AwsProperties awsProperties) {
        this.awsProperties = awsProperties;
    }

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(awsProperties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(awsProperties.getAccessKey(), awsProperties.getSecretKey())
                ));

        if (awsProperties.getEndpoint() != null && !awsProperties.getEndpoint().isBlank()) {
            builder = builder
                    .endpointOverride(URI.create(awsProperties.getEndpoint()))
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
                    .bucket(awsProperties.getBucketName())
                    .build());
            log.info("Bucket '{}' already exists", awsProperties.getBucketName());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                s3Client.createBucket(CreateBucketRequest.builder()
                        .bucket(awsProperties.getBucketName())
                        .build());
                log.info("Created bucket '{}'", awsProperties.getBucketName());
            } else {
                log.warn("Could not check/create bucket '{}': {}", awsProperties.getBucketName(), e.getMessage());
            }
        } catch (RuntimeException e) {
            log.warn("S3/MinIO is not reachable at '{}': {}. File uploads will fail until it's available.",
                    awsProperties.getEndpoint(), e.getMessage());
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
                    """.formatted(awsProperties.getBucketName());

            s3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
                    .bucket(awsProperties.getBucketName())
                    .policy(policy)
                    .build());
            log.info("Bucket '{}' is now publicly readable", awsProperties.getBucketName());
        } catch (RuntimeException e) {
            log.warn("Could not set public-read policy on bucket '{}': {}", awsProperties.getBucketName(), e.getMessage());
        }
    }
}
