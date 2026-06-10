package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ProductByCategoryReport {
    private String category;
    private Long productCount;
    private Long quantitySold;
    private BigDecimal revenue;
}
