package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SalesBySourceReport {
    private SourceData agent;
    private SourceData direct;

    @Data
    @Builder
    public static class SourceData {
        private BigDecimal totalRevenue;
        private Long totalOrders;
        private BigDecimal percentageOfRevenue;
    }
}
