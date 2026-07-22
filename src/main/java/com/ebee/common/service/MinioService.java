package com.ebee.common.service;

import com.ebee.common.config.MinioProperties;
import com.ebee.common.port.StoragePort;
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
public class MinioService implements StoragePort {

    private final S3Client s3Client;
    private final MinioProperties minioProperties;
    private final ImageConversionService imageConversionService;

    private static final java.util.Set<String> ALLOWED_CONTENT_TYPES = java.util.Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    public String uploadFile(MultipartFile file, String folder, String objectName) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("File type '" + contentType + "' is not allowed");
        }

        String safeObjectName = objectName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String ext = ".webp";
        String uploadContentType = "image/webp";
        byte[] fileBytes;

        try {
            if (imageConversionService.isImage(contentType)) {
                fileBytes = imageConversionService.convertToWebP(file);
                log.debug("Converted {} to WebP: {} bytes", file.getOriginalFilename(), fileBytes.length);
            } else {
                ext = getOriginalExtension(file.getOriginalFilename());
                uploadContentType = contentType;
                fileBytes = file.getBytes();
            }
        } catch (Exception e) {
            log.error("Failed to process file {}: {}", file.getOriginalFilename(), e.getMessage(), e);
            throw new RuntimeException("Failed to process file", e);
        }

        String fileName = folder + "/" + safeObjectName + "_" + java.util.UUID.randomUUID() + ext;

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(minioProperties.getBucketName())
                    .key(fileName)
                    .contentType(uploadContentType)
                    .build();

            s3Client.putObject(putObjectRequest,
                    software.amazon.awssdk.core.sync.RequestBody.fromBytes(fileBytes));

            return buildFileUrl(fileName);

        } catch (S3Exception e) {
            log.error("Failed to upload file to MinIO: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload file to MinIO", e);
        } catch (Exception e) {
            log.error("Failed to upload file to MinIO: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload file to MinIO", e);
        }
    }

    private String getOriginalExtension(String originalFilename) {
        if (originalFilename != null && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return "";
    }

    @Async
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return;
        }

        try {
            String key = extractKeyFromUrl(fileUrl);

            s3Client.deleteObject(b -> b
                    .bucket(minioProperties.getBucketName())
                    .key(key)
            );

        } catch (S3Exception e) {
            log.error("Failed to delete file from MinIO: {}", e.getMessage(), e);
        }
    }

    private String buildFileUrl(String fileName) {
        if (minioProperties.getPublicUrl() != null && !minioProperties.getPublicUrl().isBlank()) {
            return minioProperties.getPublicUrl() + "/" + minioProperties.getBucketName() + "/" + fileName;
        }
        if (minioProperties.getEndpoint() != null && !minioProperties.getEndpoint().isBlank()) {
            return minioProperties.getEndpoint() + "/" + minioProperties.getBucketName() + "/" + fileName;
        }
        return "https://s3." + minioProperties.getRegion() + ".amazonaws.com/" + minioProperties.getBucketName() + "/" + fileName;
    }

    private String extractKeyFromUrl(String fileUrl) {
        String baseUrl = buildFileUrl("");
        return fileUrl.replace(baseUrl, "");
    }
}
