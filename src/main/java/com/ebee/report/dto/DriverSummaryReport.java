package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DriverSummaryReport {
    private Long totalDrivers;
    private Long activeCount;
    private Long suspendedCount;
    private Long offlineCount;
    private Long onTripCount;
}
