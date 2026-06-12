package com.ebee.driver.dto;

import com.ebee.driver.enums.DriverAvailability;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class DriverWithCredentialsResponse {
    private UUID id;
    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private String licenseNumber;
    private DriverAvailability availability;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
