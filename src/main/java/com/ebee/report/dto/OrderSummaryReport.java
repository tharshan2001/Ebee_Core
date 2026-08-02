package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OrderSummaryReport {
    private Long totalOrders;
    private Long pendingCount;
    private Long confirmedCount;
    private Long processingCount;
    private Long shippedCount;
    private Long deliveredCount;
    private Long cancelledCount;
    private Long returnedCount;
    private Long failedCount;
    private Long returnExchangeCount;
    private BigDecimal cancellationRate;
    private PeriodRange period;
}
