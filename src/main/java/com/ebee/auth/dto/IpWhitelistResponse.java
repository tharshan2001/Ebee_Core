package com.ebee.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class IpWhitelistResponse {

    private UUID id;
    private UUID userId;
    private String userEmail;
    private String ipAddress;
    private String createdByEmail;
    private LocalDateTime createdAt;
}
