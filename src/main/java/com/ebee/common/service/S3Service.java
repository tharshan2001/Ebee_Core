package com.ebee.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.bucket-name}")
    private String bucketName;

    @Value("${aws.region}")
    private String region;

    @Async
    public CompletableFuture<String> uploadFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        String fileName = folder + "/" + java.util.UUID.randomUUID() + "-" + file.getOriginalFilename();

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest,
                    software.amazon.awssdk.core.sync.RequestBody.fromBytes(file.getBytes()));

            String url = "https://s3." + region + ".amazonaws.com/" + bucketName + "/" + fileName;
            return CompletableFuture.completedFuture(url);

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
                    .bucket(bucketName)
                    .key(key)
            );

        } catch (S3Exception e) {
            log.error("Failed to delete file from S3: {}", e.getMessage(), e);
        }
    }

    private String extractKeyFromUrl(String fileUrl) {
        String baseUrl = "https://s3." + region + ".amazonaws.com/" + bucketName + "/";
        return fileUrl.replace(baseUrl, "");
    }
}
