package com.ebee.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class IpWhitelistRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotBlank(message = "IP address is required")
    private String ipAddress;
}
