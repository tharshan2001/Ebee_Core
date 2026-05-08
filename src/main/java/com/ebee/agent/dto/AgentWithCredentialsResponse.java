package com.ebee.agent.dto;

import com.ebee.user.enums.UserStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AgentWithCredentialsResponse {

    private UUID agentId;
    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private String agentCode;
    private String phoneNumber;
    private Double commissionRate;
    private String nicNumber;
    private String bankAccountNumber;
    private String bankName;
    private String bankBranch;
    private UserStatus userStatus;
    private Boolean isActive;
    private Boolean mustChangePassword;
    private LocalDateTime createdAt;
    private String generatedPassword;
}