package com.ebee.reporting.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ReportSummaryResponse {

    private PeriodInfo period;
    private SalesInfo sales;
    private FinanceInfo finance;
    private DashboardInfo dashboard;

    @Data
    @Builder
    public static class PeriodInfo {
        private LocalDate start;
        private LocalDate end;
    }

    @Data
    @Builder
    public static class SalesInfo {
        private BigDecimal totalRevenue;
        private Long totalOrders;
        private BigDecimal averageOrderValue;
        private BigDecimal totalCommission;
        private Long ordersToday;
        private Long ordersYesterday;
        private Long deliveriesToday;
        private Long deliveriesYesterday;
        private PeriodComparison previousPeriod;
    }

    @Data
    @Builder
    public static class FinanceInfo {
        private BigDecimal collectedRevenue;
        private BigDecimal outstandingRevenue;
        private BigDecimal totalExpenses;
        private BigDecimal netProfit;
        private List<CategoryBreakdown> expenseByCategory;
    }

    @Data
    @Builder
    public static class DashboardInfo {
        private Long activeAgents;
        private Long activeCustomers;
        private Long lowStockProducts;
    }

    @Data
    @Builder
    public static class CategoryBreakdown {
        private String category;
        private BigDecimal amount;
    }

    @Data
    @Builder
    public static class PeriodComparison {
        private BigDecimal revenueChange;
        private BigDecimal revenueChangePercent;
        private Long ordersChange;
        private Long ordersChangePercent;
    }
}
