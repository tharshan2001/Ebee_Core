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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
