package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class InventorySummaryReport {
    private Long totalStockQty;
    private BigDecimal totalStockValue;
    private Long lowStockCount;
    private Long outOfStockCount;
}
