package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class FinancialReport {
    private BigDecimal totalRevenue;
    private BigDecimal collectedRevenue;
    private BigDecimal outstandingRevenue;
    private BigDecimal totalCostPrice;
    private BigDecimal grossProfit;
    private BigDecimal grossMargin;
    private BigDecimal totalCommissions;
    private BigDecimal totalExpenses;
    private BigDecimal netProfit;
    private PeriodRange period;
}
