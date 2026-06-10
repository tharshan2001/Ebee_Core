package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class CommissionByAgentReport {
    private UUID agentId;
    private String agentCode;
    private String agentName;
    private Long orderCount;
    private BigDecimal totalCommission;
    private BigDecimal paidAmount;
    private BigDecimal pendingAmount;
}
