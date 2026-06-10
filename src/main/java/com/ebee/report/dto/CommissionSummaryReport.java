package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CommissionSummaryReport {
    private BigDecimal totalApproved;
    private BigDecimal totalPaid;
    private BigDecimal totalProcessing;
    private BigDecimal totalPending;
    private BigDecimal outstandingLiability;
    private PeriodRange period;
}
