package com.ebee.driver.dto;

import com.ebee.driver.enums.DriverAvailability;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class DriverResponse {
    private UUID id;
    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private String userName;
    private String userEmail;
    private String userPhone;
    private String licenseNumber;
    private String contactNumber;
    private DriverAvailability availability;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
