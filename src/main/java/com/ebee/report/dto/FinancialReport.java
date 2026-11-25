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
    /** Sold lines without a recorded cost (pre-FIFO orders); when > 0, totalCostPrice is understated. */
    private long linesMissingCost;
    private BigDecimal grossProfit;
    private BigDecimal grossMargin;
    private BigDecimal totalCommissions;
    private BigDecimal totalExpenses;
    private BigDecimal netProfit;
    private PeriodRange period;
}
