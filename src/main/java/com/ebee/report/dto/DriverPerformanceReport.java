package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class DriverPerformanceReport {
    private UUID driverId;
    private String driverName;
    private Long deliveriesAssigned;
    private Long deliveriesCompleted;
    private Long deliveriesFailed;
    private Double successRate;
    private Double avgDeliveryHours;
}
