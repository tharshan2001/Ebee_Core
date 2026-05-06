package com.ebee.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AgentResponse {

    private UUID id;
    private UUID userId;
    private String agentCode;
    private String phoneNumber;
    private Double commissionRate;
    private Double totalSales;
    private Double totalCommission;
    private Boolean isActive;

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
