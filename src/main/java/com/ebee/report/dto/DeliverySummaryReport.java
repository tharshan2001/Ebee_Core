package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeliverySummaryReport {
    private Long totalDeliveries;
    private Long successCount;
    private Long failCount;
    private Double successRate;
    private Double avgDeliveryHours;
    private PeriodRange period;
}
