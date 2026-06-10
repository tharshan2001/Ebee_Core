package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeliveryFailureReasonReport {
    private String reason;
    private Long count;
    private Double percentage;
}
