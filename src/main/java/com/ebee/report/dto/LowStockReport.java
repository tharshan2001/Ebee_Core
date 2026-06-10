package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class LowStockReport {
    private UUID productId;
    private String productName;
    private String itemCode;
    private Integer currentStock;
    private Integer reorderLevel;
    private Integer shortfall;
}
