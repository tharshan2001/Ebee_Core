package com.ebee.purchase.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AttachmentResponse(
        UUID id,
        String fileUrl,
        String fileName,
        String fileType,
        LocalDateTime createdAt
) {}
