package com.ebee.agent.dto;

import com.ebee.agent.enums.AgentCommissionType;
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
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String postalCode;
    private String district;
    private String country;
    private String phoneNumber;
    private Double commissionRate;
    private Double effectiveCommissionRate;
    private String nicNumber;
    private String bankAccountNumber;
    private String bankName;
    private String bankBranch;
    private AgentCommissionType commissionType;
    private UserStatus userStatus;
    private Boolean isActive;
    private Boolean mustChangePassword;
    private LocalDateTime createdAt;
}