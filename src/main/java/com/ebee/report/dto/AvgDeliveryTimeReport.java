package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AvgDeliveryTimeReport {
    private Double avgHours;
    private Double minHours;
    private Double maxHours;
    private Long deliveredCount;
}
