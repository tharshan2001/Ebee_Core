package com.ebee.auth.dto;

import com.ebee.user.enums.RoleType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AuthResponse {

    private String token;
    private String refreshToken;
    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private RoleType role;
    private Boolean mustChangePassword;
    private LocalDateTime expiresAt;
    private UUID driverId;
    private String licenseNumber;
    private String contactNumber;
    private String driverAvailability;
}
