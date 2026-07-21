package com.ebee.driver.dto;

import com.ebee.driver.enums.DriverAvailability;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Data
public class DriverProfileResponse {
    private UUID driverId;
    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String licenseNumber;
    private DriverAvailability availability;
    private Boolean isActive;
    private String profilePictureUrl;
    private String profilePictureName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @JsonProperty("id")
    public String getId() {
        return driverId != null ? driverId.toString() : null;
    }

    @JsonProperty("photoUrl")
    public String getPhotoUrl() {
        return profilePictureUrl;
    }

    @JsonProperty("memberSince")
    public String getMemberSince() {
        return createdAt != null ? createdAt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) : null;
    }
}