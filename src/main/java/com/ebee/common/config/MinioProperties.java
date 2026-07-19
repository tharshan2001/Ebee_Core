package com.ebee.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    private final String accessKey;
    private final String secretKey;
    private final String region;
    private final String bucketName;
    private final String endpoint;
    private final String publicUrl;

    @ConstructorBinding
    public MinioProperties(String accessKey, String secretKey, String region, String bucketName, String endpoint, String publicUrl) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.region = region;
        this.bucketName = bucketName;
        this.endpoint = endpoint;
        this.publicUrl = publicUrl;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getRegion() {
        return region;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getPublicUrl() {
        return publicUrl;
    }
}
