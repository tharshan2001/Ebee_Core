package com.ebee.expense.dto;

import java.math.BigDecimal;

/** Headline figures for the expenses screen. */
public record ExpenseSummary(long pendingCount, BigDecimal pendingAmount, BigDecimal approvedThisMonth,
                             BigDecimal approvedLastMonth) {
}
