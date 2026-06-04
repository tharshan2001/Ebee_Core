package com.ebee.sales.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DashboardPreviousPeriod {
    private BigDecimal totalRevenue;
    private Long totalOrders;
    private BigDecimal totalCommission;
}
