package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CustomerSummaryReport {
    private Long totalCustomers;
    private Long newCustomersThisPeriod;
    private Long repeatOrderCount;
    private Double repeatRate;
    private PeriodRange period;
}
