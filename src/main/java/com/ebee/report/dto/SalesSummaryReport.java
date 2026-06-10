package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SalesSummaryReport {
    private BigDecimal totalRevenue;
    private Long totalOrders;
    private BigDecimal averageOrderValue;
    private BigDecimal totalCommission;
    private PeriodRange period;
    private PreviousPeriodComparison previousPeriod;

    @Data
    @Builder
    public static class PreviousPeriodComparison {
        private BigDecimal totalRevenue;
        private Long totalOrders;
        private BigDecimal totalCommission;
        private BigDecimal revenueChangePercent;
        private BigDecimal ordersChangePercent;
    }
}
