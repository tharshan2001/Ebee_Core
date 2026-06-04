package com.ebee.user.dto;

import com.ebee.user.enums.RoleType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class UserProfileResponse {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private RoleType role;
    private String profilePictureUrl;
    private Boolean loginAlertsEnabled;
    private Boolean phoneVerified;
    private LocalDateTime createdAt;
}
