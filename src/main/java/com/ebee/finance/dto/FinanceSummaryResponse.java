package com.ebee.finance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class FinanceSummaryResponse {
    private BigDecimal totalRevenue;
    private BigDecimal totalCommissions;
    private BigDecimal totalExpenses;
    private BigDecimal netProfit;
    private PeriodInfo period;
    private List<CategoryBreakdown> expenseByCategory;
    private List<BudgetUtilization> budgetUtilization;
}
