package com.ebee.common.service;

import com.ebee.common.config.AwsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;
    private final AwsProperties awsProperties;

    private static final java.util.Set<String> ALLOWED_CONTENT_TYPES = java.util.Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    public String uploadFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("File type '" + contentType + "' is not allowed");
        }

        String originalName = file.getOriginalFilename();
        String safeName = originalName != null ? originalName.replaceAll("[^a-zA-Z0-9._-]", "_") : "file";
        String fileName = folder + "/" + java.util.UUID.randomUUID() + "-" + safeName;

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(awsProperties.getBucketName())
                    .key(fileName)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest,
                    software.amazon.awssdk.core.sync.RequestBody.fromBytes(file.getBytes()));

            return buildFileUrl(fileName);

        } catch (S3Exception e) {
            log.error("Failed to upload file to S3: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload file to S3", e);
        } catch (Exception e) {
            log.error("Failed to upload file to S3: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    @Async
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return;
        }

        try {
            String key = extractKeyFromUrl(fileUrl);

            s3Client.deleteObject(b -> b
                    .bucket(awsProperties.getBucketName())
                    .key(key)
            );

        } catch (S3Exception e) {
            log.error("Failed to delete file from S3: {}", e.getMessage(), e);
        }
    }

    private String buildFileUrl(String fileName) {
        if (awsProperties.getPublicUrl() != null && !awsProperties.getPublicUrl().isBlank()) {
            return awsProperties.getPublicUrl() + "/" + awsProperties.getBucketName() + "/" + fileName;
        }
        if (awsProperties.getEndpoint() != null && !awsProperties.getEndpoint().isBlank()) {
            return awsProperties.getEndpoint() + "/" + awsProperties.getBucketName() + "/" + fileName;
        }
        return "https://s3." + awsProperties.getRegion() + ".amazonaws.com/" + awsProperties.getBucketName() + "/" + fileName;
    }

    private String extractKeyFromUrl(String fileUrl) {
        String baseUrl = buildFileUrl("");
        return fileUrl.replace(baseUrl, "");
    }
}
