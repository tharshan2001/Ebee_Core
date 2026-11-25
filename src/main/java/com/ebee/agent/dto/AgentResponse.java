package com.ebee.agent.dto;

import com.ebee.agent.enums.AgentCommissionType;
import com.ebee.user.enums.UserStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AgentResponse {

    private UUID id;
    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String agentCode;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String postalCode;
    private String district;
    private String country;
    private String phoneNumber;
    private java.math.BigDecimal commissionRate;
    private java.math.BigDecimal effectiveCommissionRate;
    private java.math.BigDecimal totalSales;
    private java.math.BigDecimal totalCommission;
    private AgentCommissionType commissionType;
    private Boolean isActive;
    private UserStatus userStatus;

    private String nicNumber;
    private String nicDocumentUrl;
    private String nicDocumentName;
    private String nicDocumentType;
    private String bankPassbookDocumentUrl;
    private String bankPassbookDocumentName;
    private String bankPassbookDocumentType;
    private String bankAccountNumber;
    private String bankName;
    private String bankBranch;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
