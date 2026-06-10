package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class SalesTrendReport {
    private LocalDate period;
    private Long totalOrders;
    private BigDecimal totalRevenue;
    private BigDecimal totalCommission;
}
