package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductSummaryReport {
    private Long totalProducts;
    private Long activeCount;
    private Long inactiveCount;
    private Long categoryCount;
}
