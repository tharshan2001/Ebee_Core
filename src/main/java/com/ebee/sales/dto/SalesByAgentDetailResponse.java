package com.ebee.sales.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class SalesByAgentDetailResponse {

    private UUID agentId;
    private String agentCode;
    private String agentName;
    private Long totalOrders;
    private BigDecimal totalRevenue;
    private BigDecimal totalCommission;
}
