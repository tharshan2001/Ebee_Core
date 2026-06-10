package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OrderSummaryReport {
    private Long totalOrders;
    private Long pendingCount;
    private Long shippedCount;
    private Long deliveredCount;
    private Long cancelledCount;
    private Long returnedCount;
    private BigDecimal cancellationRate;
    private PeriodRange period;
}
