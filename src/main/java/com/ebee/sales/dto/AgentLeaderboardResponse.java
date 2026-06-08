package com.ebee.sales.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class AgentLeaderboardResponse {

    private int rank;
    private UUID agentId;
    private String agentCode;
    private String agentName;
    private String district;
    private Long totalOrders;
    private BigDecimal totalRevenue;
    private BigDecimal totalCommission;
    private BigDecimal avgOrderValue;
}
