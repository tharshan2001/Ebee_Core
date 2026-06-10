package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SalesByStatusReport {
    private String status;
    private Long count;
    private BigDecimal totalRevenue;
    private BigDecimal percentage;
}
