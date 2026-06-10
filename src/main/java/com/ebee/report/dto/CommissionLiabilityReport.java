package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CommissionLiabilityReport {
    private BigDecimal totalUnpaid;
    private BigDecimal approvedAmount;
    private BigDecimal processingAmount;
    private Long approvedCount;
    private Long processingCount;
}
