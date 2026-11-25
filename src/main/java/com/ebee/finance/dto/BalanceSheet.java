package com.ebee.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Assets − liabilities = equity. Stock is valued at its current FIFO cost (not as of a past
 * date) — see {@link #stockValuedAtCurrentCost}.
 */
public record BalanceSheet(
        LocalDate asOf,
        List<Line> cashAndBank,
        BigDecimal totalCashAndBank,
        BigDecimal customerReceivables,
        BigDecimal stockAtCost,
        BigDecimal agentClawbacksReceivable,
        BigDecimal supplierAdvances,
        BigDecimal vatRefundable,
        BigDecimal totalAssets,
        BigDecimal supplierPayables,
        BigDecimal commissionPayable,
        BigDecimal customerAdvances,
        BigDecimal customerCredits,
        BigDecimal vatPayable,
        BigDecimal totalLiabilities,
        BigDecimal equity,
        boolean stockValuedAtCurrentCost) {

    public record Line(String name, BigDecimal amount) {
    }
}
