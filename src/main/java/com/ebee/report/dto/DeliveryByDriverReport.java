package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class DeliveryByDriverReport {
    private UUID driverId;
    private String driverName;
    private Long totalDeliveries;
    private Long successCount;
    private Long failCount;
    private Double successRate;
}
