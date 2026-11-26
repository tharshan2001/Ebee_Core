package com.ebee.accounting.dto;

import com.ebee.accounting.enums.PeriodStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One month's postings grouped by what they were (order invoiced, customer payment, supplier bill, ...)
 * and how the period's status changed. Period-end snapshots are left out of {@code lines}.
 */
public record PeriodSummaryResponse(
    AccountingPeriodResponse period,
    long postingCount,
    List<Line> lines,
    List<StatusChange> history
) {
    public record Line(String referenceType, String transactionType, long count, BigDecimal debit, BigDecimal credit) {}

    public record StatusChange(PeriodStatus previousStatus, PeriodStatus newStatus, LocalDateTime changedAt,
                               String changedByName, String reason) {}
}
